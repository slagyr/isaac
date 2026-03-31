(ns isaac.llm.ollama
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]))

;; region ----- HTTP -----

(defn- post-json!
  "POST JSON to a URL. Returns parsed response or error map."
  [url body]
  (try
    (let [resp (http/post url {:body    (json/generate-string body)
                               :headers {"Content-Type" "application/json"}
                               :timeout 120000})]
      (json/parse-string (:body resp) true))
    (catch java.net.ConnectException _
      {:error :connection-refused :message "Could not connect to Ollama server"})
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

(defn- post-json-stream!
  "POST JSON to a URL and process response as a stream of newline-delimited JSON.
   Calls on-chunk with each parsed JSON object. Returns final chunk or error."
  [url body on-chunk]
  (try
    (let [resp (http/post url {:body    (json/generate-string body)
                               :headers {"Content-Type" "application/json"}
                               :timeout 120000
                               :as      :stream})]
      (with-open [rdr (io/reader (:body resp))]
        (loop [last-chunk nil]
          (if-let [line (.readLine rdr)]
            (if (str/blank? line)
              (recur last-chunk)
              (let [chunk (json/parse-string line true)]
                (on-chunk chunk)
                (recur chunk)))
            last-chunk))))
    (catch java.net.ConnectException _
      {:error :connection-refused :message "Could not connect to Ollama server"})
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

;; endregion ^^^^^ HTTP ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a chat request to Ollama. Returns the parsed response or error map."
  [request & [{:keys [base-url] :or {base-url "http://localhost:11434"}}]]
  (let [url  (str base-url "/api/chat")
        body (assoc request :stream false)]
    (post-json! url body)))

(defn chat-stream
  "Send a streaming chat request to Ollama. Calls on-chunk for each chunk.
   Returns the final response or error map."
  [request on-chunk & [{:keys [base-url] :or {base-url "http://localhost:11434"}}]]
  (let [url  (str base-url "/api/chat")
        body (assoc request :stream true)]
    (post-json-stream! url body on-chunk)))

(defn- has-tool-calls? [response]
  (seq (get-in response [:message :tool_calls])))

(defn- extract-tool-calls
  "Extract tool calls from an Ollama response into Isaac's format."
  [response]
  (mapv (fn [tc]
          {:type      "toolCall"
           :id        (str (java.util.UUID/randomUUID))
           :name      (get-in tc [:function :name])
           :arguments (get-in tc [:function :arguments])})
        (get-in response [:message :tool_calls])))

;; endregion ^^^^^ Public API ^^^^^

;; region ----- Tool Call Loop -----

(defn chat-with-tools
  "Execute a chat with tool call loop.
   Returns {:response map :tool-calls [...] :token-counts {:inputTokens n :outputTokens n}}"
  [request tool-fn & [{:keys [base-url max-loops] :or {max-loops 10} :as opts}]]
  (loop [req          request
         all-tools    []
         total-input  0
         total-output 0
         loops        0]
    (let [response (chat req opts)]
      (if (:error response)
        response
        (let [input  (+ total-input (or (:prompt_eval_count response) 0))
              output (+ total-output (or (:eval_count response) 0))]
          (if (and (has-tool-calls? response) (< loops max-loops))
            (let [tool-calls    (extract-tool-calls response)
                  assistant-msg {:role       "assistant"
                                 :content    (or (get-in response [:message :content]) "")
                                 :tool_calls (get-in response [:message :tool_calls])}
                  tool-results  (mapv (fn [tc]
                                        {:role    "tool"
                                         :content (tool-fn (:name tc) (:arguments tc))})
                                      tool-calls)
                  new-messages  (into (:messages req)
                                      (cons assistant-msg tool-results))]
              (recur (assoc req :messages new-messages)
                     (into all-tools tool-calls)
                     input output (inc loops)))
            {:response     response
             :tool-calls   all-tools
             :token-counts {:inputTokens input :outputTokens output}}))))))

;; endregion ^^^^^ Tool Call Loop ^^^^^
