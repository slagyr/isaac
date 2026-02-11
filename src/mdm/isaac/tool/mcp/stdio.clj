(ns mdm.isaac.tool.mcp.stdio
  "MCP stdio transport client - connects to local MCP servers via subprocess stdin/stdout."
  (:require [clojure.string :as str]
            [mdm.isaac.tool.mcp.protocol :as protocol])
  (:import [java.io BufferedReader BufferedWriter InputStreamReader OutputStreamWriter]))

;; Process configuration

(defn process-config
  "Create a process configuration from a command and optional options.
   Command is a vector of strings (e.g., [\"npx\" \"@anthropic/mcp-server-filesystem\" \"/tmp\"]).
   Options may include :env (map of environment variables)."
  ([command] (process-config command {}))
  ([command opts]
   (cond-> {:command command
            :state :stopped}
     (:env opts) (assoc :env (:env opts)))))

;; Process lifecycle

(defn start!
  "Start a subprocess for the MCP server.
   Returns updated config with :process, :stdin, :stdout, :stderr, and :state :running.
   On failure returns config with :state :error and :error message."
  [config]
  (try
    (let [builder (ProcessBuilder. ^java.util.List (:command config))
          _ (when-let [env (:env config)]
              (let [proc-env (.environment builder)]
                (doseq [[k v] env]
                  (.put proc-env k v))))
          _ (.redirectErrorStream builder false)
          process (.start builder)
          stdin (BufferedWriter. (OutputStreamWriter. (.getOutputStream process)))
          stdout (BufferedReader. (InputStreamReader. (.getInputStream process)))
          stderr (BufferedReader. (InputStreamReader. (.getErrorStream process)))]
      (assoc config
             :process process
             :stdin stdin
             :stdout stdout
             :stderr stderr
             :state :running))
    (catch Exception e
      (assoc config
             :state :error
             :error (.getMessage e)))))

(defn stop!
  "Stop a running MCP subprocess. Closes streams and destroys the process.
   Returns config with :state :stopped."
  [process-state]
  (when-let [^Process p (:process process-state)]
    (try
      (when-let [^BufferedWriter stdin (:stdin process-state)]
        (.close stdin))
      (catch Exception _))
    (.destroyForcibly p)
    (.waitFor p))
  (assoc process-state
         :process nil
         :stdin nil
         :stdout nil
         :stderr nil
         :state :stopped))

;; Message framing - newline-delimited JSON

(defn frame-message
  "Frame a request map as a newline-delimited JSON string."
  [request]
  (str (protocol/request->json request) "\n"))

(defn parse-message
  "Parse a newline-delimited JSON line into a response map."
  [line]
  (protocol/json->response (str/trim line)))

;; Stdio transport

(defn- send-message!
  "Write a JSON-RPC request to the process stdin."
  [^BufferedWriter stdin request]
  (let [msg (frame-message request)]
    (.write stdin msg)
    (.flush stdin)))

(defn- read-message!
  "Read a JSON-RPC response from the process stdout."
  [^BufferedReader stdout]
  (when-let [line (.readLine stdout)]
    (parse-message line)))

(defn create-stdio-transport
  "Create a stdio transport function from a running process.
   Returns a function compatible with protocol/initialize!, list-tools!, etc.
   The transport function signature: (fn [client request] -> {:body response :headers headers})"
  [process-state]
  (fn [_client request]
    (if (= :running (:state process-state))
      (do
        (send-message! (:stdin process-state) request)
        (let [response (read-message! (:stdout process-state))]
          {:body response
           :headers {}}))
      {:body {:error {:code -32000
                      :message "Process is not running"}}})))
