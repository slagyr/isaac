(ns isaac.tool.registry-spec
  (:require
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
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
        (should (re-find #"handler failed" (:error result)))))

    (it "does not double-wrap a handler result that is already normalized"
      (sut/register! {:name    "read"
                      :handler (fn [_] {:result "file contents"})})
      (let [result (sut/execute "read" {})]
        (should= "file contents" (:result result))
        (should-be-nil (:isError result))))

    (it "returns an error map when the handler returns nil"
      (sut/register! {:name "nil-tool" :handler (fn [_] nil)})
      (let [result (sut/execute "nil-tool" {})]
        (should (:isError result))
        (should (re-find #"nil" (:error result)))))

    (it "treats disallowed tools as unknown tools"
      (sut/register! {:name "read" :handler identity})
      (let [result (sut/execute "read" {} #{"write"})]
        (should (:isError result))
        (should (re-find #"unknown tool" (:error result))))))

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
        (should (re-find #"oops" (f "fail" {})))))

    (it "returns an error string for disallowed tools"
      (sut/register! {:name "read" :handler (fn [_] "ok")})
      (let [f (sut/tool-fn #{"write"})]
        (should (re-find #"unknown tool" (f "read" {}))))))

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
        (should-be-nil (:handler d))))

    (it "filters tool definitions by the allowed tool names"
      (sut/register! {:name "read" :description "Read" :parameters {} :handler identity})
      (sut/register! {:name "write" :description "Write" :parameters {} :handler identity})
      (let [defs (sut/tool-definitions #{"read"})]
        (should= ["read"] (mapv :name defs)))))

  ;; endregion ^^^^^ Tool Definitions for Prompts ^^^^^

  ;; region ----- Logging -----

  (describe "execution logging"

    (helper/with-captured-logs)

    (it "logs tool execution start"
      (sut/register! {:name "greet" :handler (fn [_] "hi")})
      (sut/execute "greet" {:name "world"})
      (should (some #(= :tool/start (:event %)) @log/captured-logs)))

    (it "logs tool result on success"
      (sut/register! {:name "echo" :handler (fn [args] (:msg args))})
      (sut/execute "echo" {:msg "hello"})
      (should (some #(= :tool/result (:event %)) @log/captured-logs)))

    (it "logs tool error on handler exception"
      (sut/register! {:name "boom" :handler (fn [_] (throw (Exception. "kaboom")))})
      (sut/execute "boom" {})
      (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
        (should-not-be-nil err)
        (should= :tool/execute-failed (:event err))))

    (it "logs tool execute-failed when handler returns isError"
      (sut/register! {:name "failer" :handler (fn [_] {:isError true :error "bad input"})})
      (sut/execute "failer" {})
      (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
        (should-not-be-nil err)
        (should= :tool/execute-failed (:event err))))

    (it "logs tool error for unknown tool"
      (sut/execute "nosuchname" {})
      (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
        (should-not-be-nil err)
        (should= :tool/execute-failed (:event err))))

    (it "includes arguments in :tool/start log entry"
      (sut/register! {:name "read" :handler (fn [_] "content")})
      (sut/execute "read" {:filePath "/etc/hosts"})
      (let [start (first (filter #(= :tool/start (:event %)) @log/captured-logs))]
        (should= {:filePath "/etc/hosts"} (:arguments start))))

    (it "includes result preview in :tool/result log entry"
      (sut/register! {:name "echo" :handler (fn [_] "file contents here")})
      (sut/execute "echo" {})
      (let [result (first (filter #(= :tool/result (:event %)) @log/captured-logs))]
        (should= "file contents here" (:result result))))

    (it "truncates large results to 200 chars in :tool/result log"
      (let [big-content (apply str (repeat 300 "x"))]
        (sut/register! {:name "big" :handler (fn [_] big-content)})
        (sut/execute "big" {})
        (let [result (first (filter #(= :tool/result (:event %)) @log/captured-logs))]
          (should= 200 (count (:result result))))))

    (it "includes arguments in :tool/execute-failed log when handler throws"
      (sut/register! {:name "boom" :handler (fn [_] (throw (Exception. "kaboom")))})
      (sut/execute "boom" {:input "data"})
      (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
        (should= {:input "data"} (:arguments err))))

    (it "includes arguments in :tool/execute-failed log when handler returns isError"
      (sut/register! {:name "failer" :handler (fn [_] {:isError true :error "bad input"})})
      (sut/execute "failer" {:key "val"})
      (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
        (should= {:key "val"} (:arguments err))))

    )

  ;; endregion ^^^^^ Logging ^^^^^

  )
