<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

type ServiceStatus = {
  service: string;
  state: string;
};

type ViewState = 'loading' | 'up' | 'unavailable';

const status = ref<ServiceStatus | null>(null);
const errorMessage = ref<string | null>(null);
const viewState = ref<ViewState>('loading');
const lastCheckedAt = ref<Date | null>(null);

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const statusUrl = `${apiBaseUrl}/api/status`;

function isServiceStatus(payload: unknown): payload is ServiceStatus {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }

  const candidate = payload as Record<string, unknown>;
  return typeof candidate.service === 'string' && typeof candidate.state === 'string';
}

const statusLabel = computed(() => {
  if (viewState.value === 'loading') {
    return 'loading';
  }

  if (viewState.value === 'up') {
    return 'UP';
  }

  return 'unavailable';
});

const statusSummary = computed(() => {
  if (viewState.value === 'loading') {
    return '正在请求后端状态接口，请稍候。';
  }

  if (viewState.value === 'up') {
    return 'API 服务已响应，诊断控制台可以与后端建立连接。';
  }

  return '暂时无法确认 API 服务状态，请检查后端进程、网络或代理配置。';
});

const formattedLastCheckedAt = computed(() => {
  if (!lastCheckedAt.value) {
    return '尚未完成';
  }

  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(lastCheckedAt.value);
});

async function loadStatus() {
  viewState.value = 'loading';
  errorMessage.value = null;

  try {
    const response = await fetch(statusUrl, {
      headers: {
        Accept: 'application/json'
      }
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const payload = (await response.json()) as unknown;

    if (!isServiceStatus(payload)) {
      throw new Error('状态响应缺少 service/state 字段');
    }

    status.value = payload;
    viewState.value = payload.state.toUpperCase() === 'UP' ? 'up' : 'unavailable';
  } catch (error) {
    status.value = null;
    viewState.value = 'unavailable';
    errorMessage.value = error instanceof Error ? error.message : '状态请求失败';
  } finally {
    lastCheckedAt.value = new Date();
  }
}

onMounted(() => {
  void loadStatus();
});
</script>

<template>
  <main class="app-shell" aria-labelledby="page-title">
    <section class="status-panel" aria-live="polite">
      <div class="eyebrow">Payment Exception Diagnostics Agent</div>

      <div class="hero-layout">
        <div class="hero-copy">
          <h1 id="page-title">支付异常诊断控制台</h1>
          <p class="lede">
            当前页面仅用于确认前端与 API 状态接口的连通性。诊断工作流尚未实现，暂不展示任何诊断结论或模拟数据。
          </p>
        </div>

        <div class="status-card" :class="`is-${viewState}`" role="status" aria-label="服务状态">
          <span class="status-indicator" aria-hidden="true"></span>
          <div>
            <p class="status-label">{{ statusLabel }}</p>
            <p class="status-summary">{{ statusSummary }}</p>
          </div>
        </div>
      </div>

      <dl class="status-details" aria-label="状态接口详情">
        <div>
          <dt>Service</dt>
          <dd>{{ status?.service ?? '等待响应' }}</dd>
        </div>
        <div>
          <dt>State</dt>
          <dd>{{ status?.state ?? statusLabel }}</dd>
        </div>
        <div>
          <dt>Endpoint</dt>
          <dd>{{ statusUrl }}</dd>
        </div>
        <div>
          <dt>Last checked</dt>
          <dd>{{ formattedLastCheckedAt }}</dd>
        </div>
      </dl>

      <div v-if="viewState === 'unavailable'" class="notice" role="alert">
        <strong>服务暂不可用。</strong>
        <span>
          请确认后端运行在 localhost:8080，或通过 VITE_API_BASE_URL 指定 API 地址。
          <template v-if="errorMessage">错误：{{ errorMessage }}。</template>
        </span>
      </div>

      <div class="workflow-note" role="note" aria-label="诊断工作流状态">
        <h2>诊断工作流尚未实现</h2>
        <p>
          后续版本会接入支付异常上下文、MCP 工具和诊断编排。当前骨架不会生成、推断或展示任何真实诊断数据。
        </p>
      </div>

      <button class="refresh-button" type="button" :disabled="viewState === 'loading'" @click="loadStatus">
        {{ viewState === 'loading' ? '正在刷新' : '刷新状态' }}
      </button>
    </section>
  </main>
</template>
