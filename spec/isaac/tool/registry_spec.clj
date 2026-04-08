(ns isaac.tool.registry-spec
  (:require
    [isaac.tool.registry :as sut]
    [speclj.core :refer :all]))

(describe "Tool Registry"

  (before (sut/clear!))

  ;; region ----- Registration -----

  (describe "registering tools"

    (it "registers a tool by name"
      (sut/register! {:name "read" :description "Read a file" :handler identity})
      (should-not-be-nil (sut/lookup "read")))

    (it "stores the full tool definition"
      (let [handler (fn [args] "result")
            tool    {:name "write" :description "Write a file" :parameters {} :handler handler}]
        (sut/register! tool)
        (let [found (sut/lookup "write")]
          (should= "write" (:name found))
          (should= "Write a file" (:description found))
          (should= handler (:handler found)))))

    (it "overwrites a previously registered tool"
      (sut/register! {:name "read" :description "v1" :handler identity})
      (sut/register! {:name "read" :description "v2" :handler identity})
      (should= "v2" (:description (sut/lookup "read"))))

    (it "returns nil for unregistered tools"
      (should-be-nil (sut/lookup "nonexistent")))

    (it "lists all registered tools"
      (sut/register! {:name "read"  :description "Read"  :handler identity})
      (sut/register! {:name "write" :description "Write" :handler identity})
      (let [names (set (map :name (sut/all-tools)))]
        (should= #{"read" "write"} names)))

    (it "returns empty list when no tools registered"
      (should= [] (sut/all-tools)))

    (it "unregisters a tool"
      (sut/register! {:name "read" :description "Read" :handler identity})
      (sut/unregister! "read")
      (should-be-nil (sut/lookup "read"))))

  ;; endregion ^^^^^ Registration ^^^^^

  ;; region ----- Execution -----

  (describe "executing tools"

    (it "calls the handler with arguments and returns result"
      (sut/register! {:name    "echo"
                      :handler (fn [args] (str "got: " (:message args)))})
      (let [result (sut/execute "echo" {:message "hello"})]
        (should= "got: hello" (:result result))
        (should-be-nil (:isError result))))

    (it "returns an error map for an unknown tool"
      (let [result (sut/execute "delete_all" {:path "/"})]
        (should (:isError result))
        (should (string? (:error result)))
        (should (re-find #"unknown tool" (:error result)))))

    (it "returns an error map when the handler throws"
      (sut/register! {:name    "boom"
                      :handler (fn [_] (throw (Exception. "handler failed")))})
      (let [result (sut/execute "boom" {})]
        (should (:isError result))
        (should (re-find #"handler failed" (:error result))))))

  ;; endregion ^^^^^ Execution ^^^^^

  ;; region ----- tool-fn -----

  (describe "tool-fn"

    (it "returns a function that executes tools by name"
      (sut/register! {:name "greet" :handler (fn [args] (str "Hello, " (:name args)))})
      (let [f (sut/tool-fn)]
        (should= "Hello, World" (f "greet" {:name "World"}))))

    (it "returns an error string for unknown tools"
      (let [f (sut/tool-fn)]
        (should (re-find #"unknown tool" (f "nosuchname" {})))))

    (it "returns an error string when handler throws"
      (sut/register! {:name "fail" :handler (fn [_] (throw (Exception. "oops")))})
      (let [f (sut/tool-fn)]
        (should (re-find #"oops" (f "fail" {}))))))

  ;; endregion ^^^^^ tool-fn ^^^^^

  ;; region ----- Tool Definitions for Prompts -----

  (describe "tool definitions for prompts"

    (it "builds tool definitions list from registered tools"
      (sut/register! {:name        "read"
                      :description "Read a file"
                      :parameters  {:type "object" :properties {:filePath {:type "string"}}}
                      :handler     identity})
      (let [defs (sut/tool-definitions)]
        (should= 1 (count defs))
        (let [d (first defs)]
          (should= "read" (:name d))
          (should= "Read a file" (:description d))
          (should-not-be-nil (:parameters d)))))

    (it "returns empty list when no tools registered"
      (should= [] (sut/tool-definitions)))

    (it "excludes the handler from tool definitions"
      (sut/register! {:name "read" :description "Read" :parameters {} :handler identity})
      (let [d (first (sut/tool-definitions))]
        (should-be-nil (:handler d)))))

  ;; endregion ^^^^^ Tool Definitions for Prompts ^^^^^

  )
