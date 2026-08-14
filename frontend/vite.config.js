import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), 'VITE_');
    return {
        plugins: [vue()],
        server: {
            port: 5173,
            proxy: env.VITE_API_BASE_URL
                ? undefined
                : {
                    '/api': {
                        target: 'http://localhost:8080',
                        changeOrigin: true
                    }
                }
        },
        preview: {
            port: 5173
        }
    };
});
