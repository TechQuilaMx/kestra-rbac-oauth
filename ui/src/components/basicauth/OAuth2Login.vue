<template>
    <div class="oauth2-login-container">
        <el-card class="login-card">
            <template #header>
                <div class="card-header">
                    <h1>Kestra SSO</h1>
                </div>
            </template>

            <div class="login-content">
                <p v-if="!error" class="login-description">
                    Sign in with your account to continue
                </p>

                <div v-if="error" class="error-message">
                    <el-alert
                        :title="error"
                        type="error"
                        :closable="true"
                        @close="error = null"
                    />
                </div>

                <el-button
                    type="primary"
                    size="large"
                    :loading="isLoading"
                    :disabled="isLoading"
                    @click="handleLogin"
                    class="login-button"
                >
                    <span v-if="!isLoading">Login with Keycloak</span>
                    <span v-else>Redirecting...</span>
                </el-button>

                <div class="login-info">
                    <p>You will be redirected to your identity provider to securely sign in.</p>
                </div>
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
import {ref, onMounted} from "vue";
import {useOAuth2Store} from "../../stores/oauth2";
import {useMiscStore} from "override/stores/misc";

const oauth2Store = useOAuth2Store();
const miscStore = useMiscStore();

const isLoading = ref(false);
const error = ref<string | null>(null);

/**
 * Initialize OAuth2 on component mount
 */
onMounted(async () => {
    try {
        const config = await miscStore.loadConfigs();
        
        if (!config.oauth2ClientId) {
            error.value = "OAuth2 is not configured. Please contact your administrator.";
            return;
        }

        oauth2Store.initialize(config);
    } catch (err: any) {
        error.value = err.message || "Failed to load configuration";
        console.error("Failed to load OAuth2 config:", err);
    }
});

/**
 * Handle login button click
 */
const handleLogin = async () => {
    try {
        isLoading.value = true;
        error.value = null;
        
        if (!oauth2Store.isInitialized) {
            error.value = "OAuth2 is not initialized. Please refresh and try again.";
            return;
        }

        oauth2Store.login();
    } catch (err: any) {
        error.value = err.message || "Login failed. Please try again.";
        console.error("Login error:", err);
        isLoading.value = false;
    }
};
</script>

<style scoped lang="scss">
.oauth2-login-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    padding: 20px;

    .login-card {
        width: 100%;
        max-width: 400px;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);

        .card-header {
            text-align: center;

            h1 {
                margin: 0;
                font-size: 28px;
                color: #333;
                font-weight: 600;
            }
        }

        .login-content {
            text-align: center;
            padding: 20px 0;

            .login-description {
                color: #666;
                font-size: 14px;
                margin-bottom: 30px;
                line-height: 1.5;
            }

            .error-message {
                margin-bottom: 20px;
            }

            .login-button {
                width: 100%;
                height: 44px;
                font-size: 16px;
                font-weight: 500;
                margin-bottom: 20px;
            }

            .login-info {
                p {
                    font-size: 12px;
                    color: #999;
                    margin: 0;
                    line-height: 1.5;
                }
            }
        }
    }
}

@media (max-width: 600px) {
    .oauth2-login-container {
        .login-card {
            max-width: 100%;
        }
    }
}
</style>
