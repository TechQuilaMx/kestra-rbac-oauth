import {apiUrlWithoutTenants} from "override/utils/route";
import {SchemasSettings} from "monaco-yaml";
import InitialFlowSchema from "../../stores/flow-schema.json";

const LOCAL_FLOW_SCHEMA_URI = `data:application/json;charset=utf-8,${encodeURIComponent(JSON.stringify(InitialFlowSchema))}`;

const withAccessToken = (uri: string): string => {
    try {
        const rawTokens = sessionStorage.getItem("oauth2_tokens");
        if (!rawTokens) {
            return uri;
        }

        const parsedTokens = JSON.parse(rawTokens);
        const token = parsedTokens?.accessToken;
        if (!token) {
            return uri;
        }

        const separator = uri.includes("?") ? "&" : "?";
        return `${uri}${separator}token=${encodeURIComponent(token)}`;
    } catch {
        return uri;
    }
};

export const yamlSchemas: () => SchemasSettings[] = () => [
    {
        fileMatch: ["flow-*.yaml"],
        uri: LOCAL_FLOW_SCHEMA_URI
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: withAccessToken(`${apiUrlWithoutTenants()}/plugins/schemas/task`)
    },
    {
        fileMatch: ["template-*.yaml"],
        uri: withAccessToken(`${apiUrlWithoutTenants()}/plugins/schemas/template`)
    },
    {
        fileMatch: ["trigger-*.yaml"],
        uri: withAccessToken(`${apiUrlWithoutTenants()}/plugins/schemas/trigger`)
    },
    {
        fileMatch: ["plugindefault-*.yaml"],
        uri: withAccessToken(`${apiUrlWithoutTenants()}/plugins/schemas/plugindefault?arrayOf=true`)
    },
    {
        fileMatch: ["dashboard-*.yaml"],
        uri: withAccessToken(`${apiUrlWithoutTenants()}/plugins/schemas/dashboard`)
    }
]
