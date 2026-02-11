(ns mdm.isaac.tool.mcp.registry
  "Bridge between MCP servers and the Isaac tool registry."
  (:require [mdm.isaac.tool.core :as tool]
            [mdm.isaac.tool.mcp.client :as mcp]))

(defn- mcp-name->keyword
  "Convert MCP tool name to namespaced keyword."
  [mcp-name]
  (keyword "mcp" mcp-name))

(defn mcp-tool->isaac-tool
  "Convert an MCP tool definition to Isaac tool format.
   Creates an execute function that calls the MCP server."
  [mcp-tool client transport-fn]
  {:name (mcp-name->keyword (:name mcp-tool))
   :description (:description mcp-tool)
   :permissions #{:external}
   :mcp-schema (:inputSchema mcp-tool)
   :execute (fn [params]
              (mcp/call-tool! client (:name mcp-tool) params transport-fn))})

(defn register-mcp-server!
  "Register all tools from an MCP server with the tool registry.
   Returns {:status :ok :tools [...]} or {:status :error ...}"
  [client transport-fn]
  (let [result (mcp/list-tools! client transport-fn)]
    (if (= :ok (:status result))
      (do
        (doseq [mcp-tool (:tools result)]
          (let [isaac-tool (mcp-tool->isaac-tool mcp-tool client transport-fn)]
            (tool/register! isaac-tool)))
        {:status :ok
         :tools (map (comp mcp-name->keyword :name) (:tools result))})
      result)))
