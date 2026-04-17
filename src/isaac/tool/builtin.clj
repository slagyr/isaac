(ns isaac.tool.builtin
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.resolution :as config]
    [isaac.session.bridge :as bridge]
    [isaac.session.storage :as storage]
    [isaac.fs :as fs])
  (:import
    [java.io File]
    [java.util.concurrent TimeUnit]))

;; region ----- Filesystem Boundaries -----

(defn- canonical-path [path]
  (.getCanonicalPath (io/file path)))

(defn- path-inside? [parent child]
  (let [parent (canonical-path parent)
        child  (canonical-path child)]
    (or (= parent child)
        (str/starts-with? child (str parent File/separator)))))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (io/file state-dir)))
    (.getParent (io/file state-dir))
    state-dir))

(defn- config-directories [state-dir]
  (set [(str state-dir "/config")
        (str state-dir "/.isaac/config")]))

(defn- crew-quarters [state-dir crew-id]
  (str state-dir "/crew/" crew-id))

(defn- allowed-directories [{:keys [session-key state-dir]}]
  (when (and session-key state-dir)
    (when-let [session (storage/get-session state-dir session-key)]
      (let [crew-id      (or (:crew session) (:agent session) "main")
            quarters     (crew-quarters state-dir crew-id)
            _            (fs/mkdirs quarters)
            cfg          (config/load-config {:home (state-dir->home state-dir)})
            directories  (or (get-in cfg [:crew crew-id :tools :directories]) [])]
        (vec (concat [quarters]
                     (keep (fn [directory]
                             (cond
                               (= :cwd directory) (:cwd session)
                               (= "cwd" directory) (:cwd session)
                               (string? directory) directory
                               :else nil))
                           directories)))))))

(defn- path-outside-error [file-path]
  {:isError true :error (str "path outside allowed directories: " file-path)})

(defn- ensure-path-allowed [args file-path]
  (when-let [directories (seq (allowed-directories args))]
    (let [denied-config? (some #(path-inside? % file-path) (config-directories (:state-dir args)))]
      (when (or denied-config?
                (not-any? #(path-inside? % file-path) directories))
        (path-outside-error file-path)))))

;; endregion ^^^^^ Filesystem Boundaries ^^^^^

;; region ----- read -----

(defn read-tool
  "Read file contents or list a directory.
   Args: {:filePath str :offset int :limit int}"
  [{:keys [filePath offset limit] :as args}]
  (or (ensure-path-allowed args filePath)
      (cond
        (not (fs/exists? filePath))
        {:isError true :error (str "not found: " filePath)}

        (when-let [entries (fs/children filePath)]
          (seq entries))
        {:result (str/join "\n" (sort (fs/children filePath)))}

        :else
        (let [lines  (str/split-lines (or (fs/slurp filePath) ""))
              start  (if offset (dec offset) 0)
              sliced (cond->> lines
                       offset (drop start)
                       limit  (take limit))]
          {:result (str/join "\n" sliced)}))))

;; endregion ^^^^^ read ^^^^^

;; region ----- write -----

(defn write-tool
  "Write content to a file, creating parent directories as needed.
   Args: {:filePath str :content str}"
  [{:keys [filePath content] :as args}]
  (or (ensure-path-allowed args filePath)
      (try
        (fs/mkdirs (fs/parent filePath))
        (fs/spit filePath content)
        {:result (str "wrote " filePath)}
        (catch Exception e
          {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ write ^^^^^

;; region ----- edit -----

(defn edit-tool
  "Replace text in a file.
   Args: {:filePath str :oldString str :newString str :replaceAll bool}"
  [{:keys [filePath oldString newString replaceAll] :as args}]
  (or (ensure-path-allowed args filePath)
      (if-not (fs/exists? filePath)
        {:isError true :error (str "not found: " filePath)}
        (let [content (or (fs/slurp filePath) "")
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
              (fs/spit filePath new-content)
              {:result (str "edited " filePath)}))))))

;; endregion ^^^^^ edit ^^^^^

;; region ----- exec -----

(def ^:private default-timeout 30000)
(def ^:private poll-interval-ms 50)

(defn start-process [{:keys [command workdir]}]
  (let [pb (doto (ProcessBuilder. ["/bin/sh" "-c" command])
             (.redirectErrorStream true))]
    (when workdir
      (.directory pb (io/file workdir)))
    (.start pb)))

(defn process-finished? [proc timeout-ms]
  (.waitFor proc timeout-ms TimeUnit/MILLISECONDS))

(defn destroy-process! [proc]
  (.destroy proc)
  (when-not (process-finished? proc 100)
    (.destroyForcibly proc)))

(defn read-process-output [proc]
  (slurp (.getInputStream proc)))

(defn process-exit-value [proc]
  (.exitValue proc))

(defn exec-tool
  "Execute a shell command.
   Args: {:command str :workdir str :timeout int}"
  [{:keys [command workdir timeout session-key]}]
  (let [timeout-ms (or timeout default-timeout)]
    (try
      (let [proc (start-process {:command command :workdir workdir})]
        (loop [elapsed 0]
          (cond
            (bridge/cancelled? session-key)
            (do
              (destroy-process! proc)
              {:error :cancelled})

            (process-finished? proc poll-interval-ms)
            (let [output (read-process-output proc)
                  exit   (process-exit-value proc)]
              (if (zero? exit)
                {:result (str/trim output)}
                {:isError true :error (str "exit " exit ": " (str/trim output))}))

            (>= elapsed timeout-ms)
            (do
              (destroy-process! proc)
              {:isError true :error "timeout exceeded"})

            :else
            (recur (+ elapsed poll-interval-ms)))))
      (catch Exception e
        {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ exec ^^^^^

;; region ----- Registration -----

(defn- allowed-tool? [allowed-tools tool-name]
  (or (nil? allowed-tools)
      (contains? allowed-tools tool-name)))

(defn register-all!
  "Register all built-in tools with the given registry."
  ([registry-ns]
   (register-all! registry-ns nil))
  ([registry-ns allowed-tools]
   (let [allowed-tools (some->> allowed-tools
                                (map (fn [tool]
                                       (cond
                                         (keyword? tool) (name tool)
                                         (string? tool)  tool
                                         :else           (str tool))))
                                set)]
     (when (allowed-tool? allowed-tools "read")
       (registry-ns {:name        "read"
                     :description "Read file contents or list a directory"
                     :parameters  {:type       "object"
                                    :properties {:filePath {:type "string" :description "Path to file or directory"}
                                                 :offset   {:type "integer" :description "Start line (1-indexed)"}
                                                 :limit    {:type "integer" :description "Max lines to return"}}
                                    :required   ["filePath"]}
                     :handler     #'read-tool}))
     (when (allowed-tool? allowed-tools "write")
       (registry-ns {:name        "write"
                     :description "Write content to a file"
                     :parameters  {:type       "object"
                                    :properties {:filePath {:type "string" :description "Path to write"}
                                                 :content  {:type "string" :description "Content to write"}}
                                    :required   ["filePath" "content"]}
                     :handler     #'write-tool}))
     (when (allowed-tool? allowed-tools "edit")
       (registry-ns {:name        "edit"
                     :description "Replace text in a file"
                     :parameters  {:type       "object"
                                    :properties {:filePath   {:type "string" :description "File to edit"}
                                                 :oldString  {:type "string" :description "Text to replace"}
                                                 :newString  {:type "string" :description "Replacement text"}
                                                 :replaceAll {:type "boolean" :description "Replace all occurrences"}}
                                    :required   ["filePath" "oldString" "newString"]}
                     :handler     #'edit-tool}))
     (when (allowed-tool? allowed-tools "exec")
       (registry-ns {:name        "exec"
                     :description "Execute a shell command"
                     :parameters  {:type       "object"
                                    :properties {:command {:type "string" :description "Command to run"}
                                                 :workdir {:type "string" :description "Working directory"}
                                                 :timeout {:type "integer" :description "Timeout in ms"}}
                                    :required   ["command"]}
                     :handler     #'exec-tool})))))

;; endregion ^^^^^ Registration ^^^^^
