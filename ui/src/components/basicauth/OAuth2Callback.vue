<template>
    <div class="oauth2-callback-container">
        <el-card class="callback-card">
            <template #header>
                <div class="card-header">
                    <h2>Completing Sign-In</h2>
                </div>
            </template>

            <div v-if="isLoading" class="loading-state">
                <el-spin :size="50" />
                <p class="loading-text">Processing your authentication...</p>
                <p class="loading-subtext">This will only take a moment.</p>
            </div>

            <div v-else-if="error" class="error-state">
                <el-alert
                    :title="error"
                    type="error"
                    :description="errorDescription"
                    show-icon
                    class="error-alert"
                />
                <router-link to="/ui/login" class="back-link">
                    <el-button type="primary" class="back-button">
                        Back to Login
                    </el-button>
                </router-link>
            </div>

            <div v-else class="success-state">
                <el-result
                    icon="success"
                    title="Sign-In Successful"
                    sub-title="Redirecting you to the application..."
                />
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
import {ref, onMounted} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useOAuth2Store} from "../../stores/oauth2";

const route = useRoute();
const router = useRouter();
const oauth2Store = useOAuth2Store();

const isLoading = ref(true);
const error = ref<string | null>(null);
const errorDescription = ref<string>("");

/**
 * Handle OAuth2 callback
 * Extract authorization code from URL query params
 * and exchange it for tokens
 */
onMounted(async () => {
    try {
        const code = route.query.code as string;
        const state = route.query.state as string;
        const errorParam = route.query.error as string;

        // Check for error from provider
        if (errorParam) {
            const errorDesc = route.query.error_description as string;
            error.value = `Authentication Error: ${errorParam}`;
            errorDescription.value = errorDesc || "Please try logging in again.";
            isLoading.value = false;
            return;
        }

        // Validate code and state
        if (!code) {
            error.value = "Invalid Callback";
            errorDescription.value = "No authorization code received from provider. Please try again.";
            isLoading.value = false;
            return;
        }

        if (!state) {
            error.value = "Invalid State";
            errorDescription.value = "State parameter is missing. This may be a CSRF attack.";
            isLoading.value = false;
            return;
        }

        // Initialize OAuth2 store if not already done
        if (!oauth2Store.isInitialized) {
            // This shouldn't happen in normal flow, but handle gracefully
            error.value = "OAuth2 Not Initialized";
            errorDescription.value = "Please go back to the login page and try again.";
            isLoading.value = false;
            return;
        }

        // Exchange code for tokens
        await oauth2Store.handleCallback(code, state);

        // Success - router will redirect to home
        // (handleCallback in store calls router.push)
    } catch (err: any) {
        error.value = err.message || "Authentication Failed";
        errorDescription.value = "There was an error during the authentication process. Please try again.";
        console.error("OAuth2 callback error:", err);
        isLoading.value = false;
    }
});
</script>

<style scoped lang="scss">
.oauth2-callback-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    padding: 20px;

    .callback-card {
        width: 100%;
        max-width: 400px;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);

        .card-header {
            text-align: center;

            h2 {
                margin: 0;
                font-size: 20px;
                color: #333;
                font-weight: 600;
            }
        }

        .loading-state {
            text-align: center;
            padding: 40px 20px;

            .loading-text {
                margin-top: 20px;
                font-size: 16px;
                color: #333;
                font-weight: 500;
            }

            .loading-subtext {
                margin-top: 10px;
                font-size: 14px;
                color: #999;
            }
        }

        .error-state {
            padding: 20px;

            .error-alert {
                margin-bottom: 20px;
            }

            .back-link {
                text-decoration: none;

                .back-button {
                    width: 100%;
                    height: 40px;
                }
            }
        }

        .success-state {
            padding: 40px 20px;
        }
    }
}

@media (max-width: 600px) {
    .oauth2-callback-container {
        .callback-card {
            max-width: 100%;
        }
    }
}
</style>
