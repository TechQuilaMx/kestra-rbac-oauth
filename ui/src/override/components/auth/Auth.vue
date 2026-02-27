<template>
    <el-select
        placement="right-end"
        :popperOffset="20"
        :showArrow="false"
        :suffixIcon="ChevronRight"
        :placeholder="$t('kestra')"
        popperClass="user-select border border-0"
    >
        <template #prefix>
            <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra" class="user-avatar">
        </template>
        <template #header>
            <el-option :value="{}" class=" list-unstyled">
                <div class="menu-item">
                    <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
                    {{ $t("kestra") }}
                </div>
            </el-option>
            <el-option :value="{}" class="list-unstyled">
                <div class="user-info">
                    <div class="user-name">{{ userLabel }}</div>
                    <div v-if="userDetails" class="user-details">{{ userDetails }}</div>
                    <div v-if="userRoles" class="user-roles">{{ userRoles }}</div>
                </div>
            </el-option>
        </template>
        <el-option label="Settings" value="settings">
            <RouterLink :to="{name: 'settings'}" class="menu-item">
                <CogOutline class="menu-icon" />
                {{ $t("settings.label") }}
            </RouterLink>
        </el-option>
        <el-option label="slack" value="slack">
            <a href="https://kestra.io/slack" target="_blank" class="menu-item">
                <Slack class="menu-icon" />
                {{ $t("join_slack") }}
            </a>
        </el-option>
        <template #footer>
            <el-option class="list-unstyled" :value="'logout'" @click="logout">
                <div class="menu-item">
                    <Logout class="menu-icon" />
                    {{ $t("setup.logout") }}
                </div>
            </el-option>
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {RouterLink, useRouter} from "vue-router";

    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import Logout from "vue-material-design-icons/Logout.vue";

    import * as BasicAuth from "../../../utils/basicAuth";
    import {useAxios} from "../../../utils/axios";
    import {useOAuth2Store} from "../../../stores/oauth2";
    import {useAuthStore} from "../../stores/auth";

    const router = useRouter();
    const axios = useAxios();
    const oauth2Store = useOAuth2Store();
    const authStore = useAuthStore();

    const userLabel = computed(() => {
        return oauth2Store.userInfo?.name
            || oauth2Store.userInfo?.username
            || oauth2Store.userInfo?.email
            || "Anonymous";
    });

    const userDetails = computed(() => {
        if (!oauth2Store.userInfo) {
            return undefined;
        }
        const email = oauth2Store.userInfo.email;
        const username = oauth2Store.userInfo.username;
        if (email && username && email !== username) {
            return `${username} • ${email}`;
        }
        return email || username;
    });

    const userRoles = computed(() => {
        const roles = oauth2Store.userInfo?.roles ?? [];
        if (!roles.length) {
            return undefined;
        }
        return roles.map((role) => role.toUpperCase()).join(" • ");
    });

    const logout = async () => {
        try {
            await authStore.logout();
        } finally {
            BasicAuth.logout();
            delete axios.defaults.headers.common["Authorization"];
            router.push({name: "login"});
        }
    };
</script>

<style scoped lang="scss">
.menu-item{
    display: flex;
    align-items: center;
    gap: 1rem;
    color: var(--ks-content-primary);

    .menu-icon {
        color: var(--ks-content-tertiary);
        font-size: 1.5rem;
    }
}
</style>

<style lang="scss">
.user-select  {
    &.el-select-dropdown {
        width: 328px;
        background: var(--ks-select-background);
        box-shadow: 2px 3px 3px var(--ks-card-shadow);
        border-radius: var(--bs-border-radius);
        border: 1px solid var(--ks-border-primary) !important;
        margin-bottom: -2px;

        .el-select-dropdown__item {
            min-height: 34px;
            height: fit-content;
            padding: 10px 16px 8px 16px;
            margin: 0;
            font-size: 14px;
            font-weight: 700;
        }

        .el-select-dropdown__header {
            .el-select-dropdown__item {
                padding: 0;
                margin: 0;
                background: none;

                &.is-hovering {
                    background: none;
                }
            }
        }

        .el-select-dropdown__footer {
            padding: 5px 0;
            .el-select-dropdown__item {
                margin: 0 !important;
            }
        }
    }
}

.el-select {
    >.el-select__wrapper {
        padding: 2px 8px;
        padding-left: 6px !important;
    }
}

html.menu-collapsed {
    .el-select__suffix {
        display: none;
    }
}

.user-avatar {
    padding: 0.25rem;
    border-radius: 0.25rem;

}

.user-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 6px 0 2px 0;
    color: var(--ks-content-primary);
}

.user-name {
    font-weight: 700;
}

.user-details,
.user-roles {
    font-size: 12px;
    color: var(--ks-content-tertiary);
}
</style>