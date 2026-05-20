(ns isaac.service.macos
  (:require
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.util.shell :as shell]))

(def ^:private label "com.slagyr.isaac")

(def ^:private plist-template
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
<plist version=\"1.0\">
<dict>
    <key>Label</key>
    <string>{LABEL}</string>
    <key>ProgramArguments</key>
    <array>
        <string>{BB_BIN}</string>
        <string>--config</string>
        <string>{BB_EDN}/bb.edn</string>
        <string>run</string>
        <string>-m</string>
        <string>isaac.main</string>
        <string>server</string>
    </array>
    <key>KeepAlive</key>
    <true/>
    <key>RunAtLoad</key>
    <true/>
    <key>StandardOutPath</key>
    <string>{LOG_DIR}/server.log</string>
    <key>StandardErrorPath</key>
    <string>{LOG_DIR}/server.log</string>
</dict>
</plist>")

(defn plist-content [{:keys [bb-bin bb-edn home log-dir]}]
  (-> plist-template
      (str/replace "{LABEL}"   label)
      (str/replace "{BB_BIN}"  bb-bin)
      (str/replace "{BB_EDN}"  bb-edn)
      (str/replace "{HOME}"    (or home ""))
      (str/replace "{LOG_DIR}" log-dir)))

(defn- user-home [] (home/user-home))
(defn- plist-path [] (str (user-home) "/Library/LaunchAgents/" label ".plist"))
(defn- log-dir []   (str (user-home) "/Library/Logs/isaac"))

(defn- uid []
  (str/trim (:out (shell/sh! "id" "-u"))))

(defn- service-target []
  (str "gui/" (uid) "/" label))

(defn- bootstrap-target []
  (str "gui/" (uid)))

(defn install! [{:keys [bb-bin bb-edn]}]
  (let [log-d   (log-dir)
        plist-p (plist-path)
        h       (user-home)
        content (plist-content {:bb-bin  bb-bin
                                :bb-edn  bb-edn
                                :home    h
                                :log-dir log-d})]
    (fs/mkdirs (fs/parent plist-p))
    (fs/mkdirs log-d)
    (fs/spit plist-p content)
    (shell/sh! "launchctl" "bootstrap" (bootstrap-target) plist-p)))

(defn uninstall! [_opts]
  (let [plist-p (plist-path)]
    (when (fs/exists? plist-p)
      (shell/sh! "launchctl" "bootout" (service-target))
      (fs/delete plist-p))))

(defn start! [_opts]
  (shell/sh! "launchctl" "bootstrap" (bootstrap-target) (plist-path)))

(defn stop! [_opts]
  (shell/sh! "launchctl" "bootout" (service-target)))

(defn restart! [_opts]
  (shell/sh! "launchctl" "kickstart" "-k" (service-target)))

(defn parse-status [output]
  {:state      (second (re-find #"state\s*=\s*(\S+)" output))
   :pid        (second (re-find #"pid\s*=\s*(\d+)" output))
   :last-exit  (second (re-find #"last exit code\s*=\s*(-?\d+)" output))})

(defn status! [_opts]
  (let [plist-p (plist-path)]
    (if-not (fs/exists? plist-p)
      {:installed? false}
      (let [result (shell/sh! "launchctl" "print" (service-target))]
        (if (zero? (:exit result))
          (assoc (parse-status (:out result)) :installed? true)
          {:installed? true :state "stopped"})))))

(defn logs! [{:keys [follow?]}]
  (let [log-file (str (log-dir) "/server.log")]
    (if (fs/exists? log-file)
      (if follow?
        (do (shell/exec! "tail" "-f" log-file)
            {:log-path log-file :content nil})
        {:log-path log-file :content (fs/slurp log-file)})
      {:log-path log-file :content nil})))
