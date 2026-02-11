(ns mdm.isaac.tool.mcp.registry-spec
  "Specs for MCP server registration with the tool system."
  (:require [mdm.isaac.tool.core :as tool]
            [mdm.isaac.tool.mcp.registry :as sut]
            [speclj.core :refer :all]))

(describe "MCP Registry Integration"

  (before (tool/clear!))

  (context "converting MCP tools to Isaac tools"

    (it "converts an MCP tool definition to Isaac tool format"
      (let [mcp-tool {:name "brave_web_search"
                      :description "Search the web using Brave Search"
                      :inputSchema {:type "object"
                                    :properties {:query {:type "string"
                                                         :description "Search query"}}
                                    :required ["query"]}}
            client {:endpoint "https://example.com/mcp" :session-id "sess-123"}
            transport-fn identity
            isaac-tool (sut/mcp-tool->isaac-tool mcp-tool client transport-fn)]
        (should= :mcp/brave_web_search (:name isaac-tool))
        (should= "Search the web using Brave Search" (:description isaac-tool))
        (should-not-be-nil (:execute isaac-tool))
        (should= #{:external} (:permissions isaac-tool))))

    (it "prefixes tool name with mcp/"
      (let [mcp-tool {:name "some_tool" :description "A tool"}
            isaac-tool (sut/mcp-tool->isaac-tool mcp-tool {} identity)]
        (should= :mcp/some_tool (:name isaac-tool)))))

  (context "registering MCP server tools"

    (with-stubs)

    (it "registers all tools from an MCP server"
      (let [mock-tools [{:name "tool1" :description "Tool 1"}
                        {:name "tool2" :description "Tool 2"}]
            mock-transport (stub :transport {:return {:body {:result {:tools mock-tools}}}})]
        (with-redefs [mdm.isaac.tool.mcp.client/list-tools!
                      (fn [_ _] {:status :ok :tools mock-tools})]
          (sut/register-mcp-server! {:endpoint "https://example.com/mcp"
                                     :session-id "sess-123"}
                                    mock-transport)
          (should-not-be-nil (tool/get-tool :mcp/tool1))
          (should-not-be-nil (tool/get-tool :mcp/tool2))))))

  )
