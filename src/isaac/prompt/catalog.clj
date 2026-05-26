;; mutation-tested: pending
(ns isaac.prompt.catalog
  (:require
    [clj-yaml.core :as yaml]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.logger :as log]))

(def ^:private default-prompt-dir-names
  {"commands" :command
   "skills"   :skill})

(defn- split-frontmatter [content]
  (when-let [[_ frontmatter body] (re-matches #"(?s)\A---\r?\n(.*?)\r?\n---\r?\n?(.*)\z" (or content ""))]
    {:body        body
     :frontmatter frontmatter}))

(defn- parse-frontmatter [content]
  (when-let [{:keys [body frontmatter]} (split-frontmatter content)]
    {:body body
     :data (yaml/parse-string frontmatter :keywords true)}))

(defn- normalize-type [value]
  (when (some? value)
    (keyword (name value))))

(defn- normalize-prompt-dir-names [config]
  (merge default-prompt-dir-names
         (into {}
               (map (fn [[dir type]]
                      [(name dir) (normalize-type type)]))
               (:prompt-dir-names config))))

(defn- join-path [root path]
  (if (str/starts-with? path "/")
    path
    (str root "/" path)))

(defn- absolute-path [path]
  (when path
    (if (str/starts-with? path "/")
      path
      (str (System/getProperty "user.dir") "/" path))))

(defn- markdown-file? [path]
  (str/ends-with? path ".md"))

(defn- collect-markdown-files [fs* path]
  (cond
    (not (fs/exists? fs* path)) []
    (fs/file? fs* path)         (if (markdown-file? path) [path] [])
    :else                       (mapcat #(collect-markdown-files fs* (str path "/" %))
                                        (or (fs/children fs* path) []))))

(defn- typed-base-files [fs* root dir-names]
  (mapcat (fn [child]
            (let [child-path (str root "/" child)]
              (when-let [type (get dir-names child)]
                (map (fn [path] {:default-type type :path path})
                     (collect-markdown-files fs* child-path)))))
          (or (fs/children fs* root) [])))

(defn- generic-root-files [fs* root]
  (map (fn [path] {:path path})
       (collect-markdown-files fs* root)))

(defn- typed-root-files [fs* root type]
  (map (fn [path] {:default-type type :path path})
       (collect-markdown-files fs* root)))

(defn- file-name [path]
  (fs/filename path))

(defn- basename [path]
  (let [filename (file-name path)]
    (subs filename 0 (- (count filename) 3))))

(defn- skill-file? [path]
  (= "SKILL.md" (file-name path)))

(defn- entry-name [path]
  (if (skill-file? path)
    (fs/filename (fs/parent path))
    (basename path)))

(defn- root-file-specs [fs* {:keys [mode path default-type]} dir-names]
  (case mode
    :typed-base  (typed-base-files fs* path dir-names)
    :typed-root  (typed-root-files fs* path default-type)
    :generic-root (generic-root-files fs* path)
    []))

(defn- relative-segments [root path]
  (let [prefix (str root "/")]
    (if (str/starts-with? path prefix)
      (str/split (subs path (count prefix)) #"/")
      [])))

(defn- path-type [dir-names root path default-type]
  (or default-type
      (some-> (first (relative-segments root path))
              (get dir-names))))

(defn- user-type [data]
  (when (contains? data :user-invocable)
    (if (:user-invocable data) :command :skill)))

(defn- effective-type [explicit user path]
  (or explicit user path))

(defn- warn-on-conflict! [name explicit inferred]
  (when (and explicit inferred (not= explicit inferred))
    (log/warn :prompt/type-conflict :name name :explicit-type explicit :inferred-type inferred)))

(defn- file-entry [fs* dir-names {:keys [default-type layer root path]}]
  (when-let [{:keys [data]} (parse-frontmatter (fs/slurp fs* path))]
    (let [name          (entry-name path)
          explicit-type (normalize-type (:type data))
          inferred-path (path-type dir-names root path default-type)
          inferred-user (user-type data)
          type          (effective-type explicit-type inferred-user inferred-path)]
      (warn-on-conflict! name explicit-type (or inferred-user inferred-path))
      (if type
        {:body-loader  (fn [] (:body (parse-frontmatter (fs/slurp fs* path))))
         :description  (:description data)
         :layer        layer
         :name         name
         :params       (:params data)
         :path         path
         :skills       (:skills data)
         :type         type}
        (do
          (log/warn :prompt/missing-type-signal :path path)
          nil)))))

(defn- dedupe-file-specs [file-specs]
  (vals (reduce (fn [acc spec] (assoc acc (:path spec) spec)) {} file-specs)))

(defn- ancestor-paths [cwd]
  (loop [path (absolute-path cwd)
         acc  []]
    (if (str/blank? path)
      acc
      (let [parent (fs/parent path)]
        (if (= path parent)
          (conj acc path)
          (recur parent (conj acc path)))))))

(defn- prompt-marker-paths [config]
  (concat [".isaac"]
          (remove str/blank?
                  (concat (:prompt-paths config)
                          (:command-paths config)
                          (:skill-paths config)))))

(defn- project-root [fs* cwd config]
  (when cwd
    (or (some (fn [ancestor]
                (when (some #(fs/exists? fs* (join-path ancestor %)) (prompt-marker-paths config))
                  ancestor))
              (ancestor-paths cwd))
        (absolute-path cwd))))

(defn- global-roots [state-dir config]
  (concat [{:layer :global :mode :typed-base :path (str state-dir "/config")}]
          (map (fn [path] {:layer :global :mode :generic-root :path (join-path state-dir path)})
               (:prompt-paths config))
          (map (fn [path] {:default-type :command :layer :global :mode :typed-root :path (join-path state-dir path)})
               (:command-paths config))
          (map (fn [path] {:default-type :skill :layer :global :mode :typed-root :path (join-path state-dir path)})
               (:skill-paths config))))

(defn- project-roots [project-root config]
  (when project-root
    (concat [{:layer :project :mode :typed-base :path (str project-root "/.isaac")}]
            (map (fn [path] {:layer :project :mode :generic-root :path (join-path project-root path)})
                 (:prompt-paths config))
            (map (fn [path] {:default-type :command :layer :project :mode :typed-root :path (join-path project-root path)})
                 (:command-paths config))
            (map (fn [path] {:default-type :skill :layer :project :mode :typed-root :path (join-path project-root path)})
                 (:skill-paths config)))))

(defn- index-entry [catalog entry]
  (case (:type entry)
    :command (assoc-in catalog [:commands (:name entry)] entry)
    :skill   (assoc-in catalog [:skills (:name entry)] entry)
    catalog))

(defn resolve-catalog [{:keys [config cwd fs state-dir]}]
  (let [fs*           fs
        config        (or config {})
        dir-names     (normalize-prompt-dir-names config)
        project-root* (project-root fs* cwd config)
        start-ns      (System/nanoTime)
        root-specs    (concat (global-roots state-dir config)
                              (project-roots project-root* config))
        file-specs    (->> root-specs
                           (mapcat (fn [root]
                                     (map #(merge root %) (root-file-specs fs* root dir-names))))
                           dedupe-file-specs)
        entries       (keep #(file-entry fs* dir-names %) file-specs)
        catalog       (reduce index-entry {:commands {} :skills {}} entries)
        elapsed-ms    (/ (- (System/nanoTime) start-ns) 1000000.0)]
    (log/debug :prompt/catalog-resolved
               :elapsed-ms elapsed-ms
               :file-count (count file-specs)
               :command-count (count (:commands catalog))
               :skill-count (count (:skills catalog)))
    catalog))
