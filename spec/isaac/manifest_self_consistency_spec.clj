(ns isaac.manifest-self-consistency-spec
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [speclj.core :refer :all]))

(defn- read-manifest [path]
  (-> path io/file slurp edn/read-string :isaac/manifest))

(defn- ensure-local-deps! [path]
  ;; Under bb, dynamically classpath the module so requiring-resolve can
  ;; find its symbols. Under JVM, the test alias in deps.edn already
  ;; pre-declares the modules (clojure.repl.deps/add-libs is REPL-only
  ;; and can't add deps from a spec-runner thread), so this is a no-op.
  (when-let [add-deps (try (requiring-resolve 'babashka.deps/add-deps)
                           (catch Throwable _ nil))]
    (when (str/starts-with? path "modules/")
      (when-let [module-root (second (re-find #"^(modules/[^/]+)" path))]
        (let [lib-name (last (str/split module-root #"/"))
              lib-sym  (symbol lib-name lib-name)]
          (add-deps {:deps {lib-sym {:local/root module-root}}}))))))

(defn- manifest-paths []
  (sort
    (concat ["deps.edn"]
            (->> ["modules" "spec/marigold"]
                 (map io/file)
                 (mapcat file-seq)
                 (filter #(.isFile %))
                 (map #(.getPath %))
                 (filter #(str/ends-with? % "deps.edn"))))))

(defn- factory-symbols [manifest]
  (->> (tree-seq coll? seq manifest)
       (filter map?)
       (mapcat #(keep % [:bootstrap :factory]))
       (filter symbol?)))

(describe "manifest self-consistency"
  (it "resolves every declared :factory and :bootstrap symbol"
    (doseq [path (manifest-paths)
            :let [manifest (read-manifest path)]
            symbol (factory-symbols manifest)]
      (ensure-local-deps! path)
      (should-not-be-nil (requiring-resolve symbol)))))
