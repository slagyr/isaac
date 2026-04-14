(ns isaac.tool.builtin
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.session.bridge :as bridge])
  (:import
    [java.util.concurrent TimeUnit]))

;; region ----- read -----

(defn read-tool
  "Read file contents or list a directory.
   Args: {:filePath str :offset int :limit int}"
  [{:keys [filePath offset limit]}]
  (let [f (io/file filePath)]
    (cond
      (not (.exists f))
      {:isError true :error (str "not found: " filePath)}

      (.isDirectory f)
      {:result (str/join "\n" (sort (map #(.getName %) (.listFiles f))))}

      :else
      (let [lines  (str/split-lines (slurp f))
            start  (if offset (dec offset) 0)        ; offset is 1-indexed
            sliced (cond->> lines
                     offset (drop start)
                     limit  (take limit))]
        {:result (str/join "\n" sliced)}))))

;; endregion ^^^^^ read ^^^^^

;; region ----- write -----

(defn write-tool
  "Write content to a file, creating parent directories as needed.
   Args: {:filePath str :content str}"
  [{:keys [filePath content]}]
  (try
    (let [f (io/file filePath)]
      (when-let [parent (.getParentFile f)]
        (.mkdirs parent))
      (spit f content)
      {:result (str "wrote " filePath)})
    (catch Exception e
      {:isError true :error (.getMessage e)})))

;; endregion ^^^^^ write ^^^^^

;; region ----- edit -----

(defn edit-tool
  "Replace text in a file.
   Args: {:filePath str :oldString str :newString str :replaceAll bool}"
  [{:keys [filePath oldString newString replaceAll]}]
  (let [f (io/file filePath)]
    (if-not (.exists f)
      {:isError true :error (str "not found: " filePath)}
      (let [content (slurp f)
            count   (count (re-seq (java.util.regex.Pattern/compile
                                     (java.util.regex.Pattern/quote oldString))
                                   content))]
        (cond
          (= 0 count)
          {:isError true :error (str "not found: " oldString)}

          (and (> count 1) (not replaceAll))
          {:isError true :error (str "multiple matches for: " oldString)}

          :else
          (let [new-content (str/replace content oldString newString)]
            (spit f new-content)
            {:result (str "edited " filePath)}))))))

;; endregion ^^^^^ edit ^^^^^

;; region ----- exec -----

(def ^:private default-timeout 30000)

(defn exec-tool
  "Execute a shell command.
   Args: {:command str :workdir str :timeout int}"
  [{:keys [command workdir timeout session-key]}]
  (let [timeout-ms (or timeout default-timeout)]
    (try
      (let [pb        (doto (ProcessBuilder. ["/bin/sh" "-c" command])
                        (.redirectErrorStream true))
            _         (when workdir (.directory pb (io/file workdir)))
            proc      (.start pb)]
        (loop [elapsed 0]
          (cond
            (bridge/cancelled? session-key)
            (do
              (.destroy proc)
              (when-not (.waitFor proc 100 TimeUnit/MILLISECONDS)
                (.destroyForcibly proc))
              {:error :cancelled})

            (.waitFor proc 50 TimeUnit/MILLISECONDS)
            (let [output (slurp (.getInputStream proc))
                  exit   (.exitValue proc)]
              (if (zero? exit)
                {:result (str/trim output)}
                {:isError true :error (str "exit " exit ": " (str/trim output))}))

            (>= elapsed timeout-ms)
            (do
              (.destroyForcibly proc)
              {:isError true :error "timeout exceeded"})

            :else
            (recur (+ elapsed 50)))))
      (catch Exception e
        {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ exec ^^^^^

;; region ----- Registration -----

(defn register-all!
  "Register all built-in tools with the given registry."
  [registry-ns]
  (registry-ns {:name        "read"
                :description "Read file contents or list a directory"
                :parameters  {:type       "object"
                               :properties {:filePath {:type "string" :description "Path to file or directory"}
                                            :offset   {:type "integer" :description "Start line (1-indexed)"}
                                            :limit    {:type "integer" :description "Max lines to return"}}
                               :required   ["filePath"]}
                :handler     read-tool})
  (registry-ns {:name        "write"
                :description "Write content to a file"
                :parameters  {:type       "object"
                               :properties {:filePath {:type "string" :description "Path to write"}
                                            :content  {:type "string" :description "Content to write"}}
                               :required   ["filePath" "content"]}
                :handler     write-tool})
  (registry-ns {:name        "edit"
                :description "Replace text in a file"
                :parameters  {:type       "object"
                               :properties {:filePath   {:type "string" :description "File to edit"}
                                            :oldString  {:type "string" :description "Text to replace"}
                                            :newString  {:type "string" :description "Replacement text"}
                                            :replaceAll {:type "boolean" :description "Replace all occurrences"}}
                               :required   ["filePath" "oldString" "newString"]}
                :handler     edit-tool})
  (registry-ns {:name        "exec"
                :description "Execute a shell command"
                :parameters  {:type       "object"
                               :properties {:command {:type "string" :description "Command to run"}
                                            :workdir {:type "string" :description "Working directory"}
                                            :timeout {:type "integer" :description "Timeout in ms"}}
                               :required   ["command"]}
                :handler     exec-tool}))

;; endregion ^^^^^ Registration ^^^^^
