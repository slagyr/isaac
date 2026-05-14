(ns isaac.bridge.status
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.slash.registry :as slash-registry]
    [isaac.system :as system]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Helpers -----

(defn- turn-count [transcript]
  (count (filter #(= "message" (:type %)) transcript)))

(defn ctx-provider-name [ctx]
  (let [p (:provider ctx)]
    (cond
      (string? p) p
      (some? p)   ((requiring-resolve 'isaac.llm.api/display-name) p)
      :else       nil)))

(defn- summarize-soul [ctx]
  (let [soul    (or (:soul ctx) "")
        source  (if (> (count (remove str/blank? (str/split (str/trim soul) #"\s+"))) 4)
                  soul
                  (or (:boot-files ctx) soul ""))
        text   (-> source
                   (str/replace #"(?m)^#+\s.*$" "")
                   (str/replace #"\[([^\]]+)\]\([^)]+\)" "$1")
                   (str/replace #"`" "")
                   (str/replace #"\s+" " ")
                   str/trim)
        words  (->> (str/split text #"\s+")
                    (remove str/blank?)
                    vec)]
    (cond
      (empty? words)
      ""

      (<= (count words) 8)
      (str/join " " words)

      :else
      (str (str/join " " (take 8 words)) " ..."))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Public API -----

(defn- session-store []
  (or (system/get :session-store)
      (file-store/create-store (system/get :state-dir))))

(defn status-data
  "Gather session and model info for the /status command."
  ([session-key ctx]
   (let [session-store  (session-store)
         entry          (store/get-session session-store session-key)
         transcript     (or (store/get-transcript session-store session-key) [])
         turns          (turn-count transcript)
         tokens         (or (:total-tokens entry) 0)
         context-window (or (:context-window ctx) 32768)
         context-pct    (if (pos? context-window)
                          (int (Math/round (* 100.0 (/ tokens context-window))))
                          0)]
     {:crew           (:crew ctx)
      :boot-files     (:boot-files ctx)
      :soul           (:soul ctx)
      :model          (:model ctx)
      :provider       (ctx-provider-name ctx)
      :session-key    session-key
      :session-file   (:session-file entry)
      :turns          turns
      :compactions    (or (:compaction-count entry) 0)
      :tokens         tokens
      :context-window context-window
      :context-pct    context-pct
      :tool-count     (count (tool-registry/all-tools))
      :cwd            (or (:cwd entry) (System/getProperty "user.dir"))}))
  ([state-dir session-key ctx]
   (system/with-system {:state-dir state-dir}
     (status-data session-key ctx))))

(defn available-commands []
  (slash-registry/all-commands (:module-index (or (config/snapshot) {}))))

(defn format-status
  "Format status data as human-readable markdown-style status lines."
  [data]
  (let [label-width 12
        line        (fn [label value]
                      (format (str "%-" label-width "s %s") label value))]
    (str "```text\n"
         (str/join "\n"
                   ["Session Status"
                    (apply str (repeat 22 "─"))
                    (line "Crew"        (:crew data))
                    (line "Model"       (str (:model data) " (" (:provider data) ")"))
                    (line "Session"     (:session-key data))
                    (line "File"        (:session-file data))
                    (line "Turns"       (:turns data))
                    (line "Compactions" (:compactions data))
                    (line "Context"     (str (format "%,d" (:tokens data)) " / "
                                              (format "%,d" (:context-window data)) " ("
                                              (:context-pct data) "%)"))
                    (line "Soul"        (str "\"" (summarize-soul data) "\""))
                    (line "Tools"       (:tool-count data))
                    (line "CWD"         (:cwd data))])
         "\n```")))

;; endregion ^^^^^ Public API ^^^^^
