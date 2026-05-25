(ns isaac.config.api
  "Public API for Isaac configuration. Clients require this namespace (aliased
   `config`) and use these functions instead of reaching into
   isaac.config.loader / install / mutate directly. Internal config namespaces
   stay free to reorganize behind this surface.

   Each entry delegates to its source at call time (rather than aliasing the
   var), so `with-redefs` on the underlying fn still takes effect for callers
   through this API. Dynamic vars (loader/*state-dir*, *config*) are not
   exposed: a `binding` must target the real var, so the rare caller that binds
   them requires loader directly."
  (:require
    [isaac.config.install :as install]
    [isaac.config.loader :as loader]
    [isaac.config.mutate :as mutate]))

;; ----- loading & snapshot -----
(defn load-config        [& args] (apply loader/load-config args))
(defn load-config-result [& args] (apply loader/load-config-result args))
(defn normalize-config   [& args] (apply loader/normalize-config args))
(defn snapshot           [& args] (apply loader/snapshot args))
(defn set-snapshot!      [& args] (apply loader/set-snapshot! args))
(defn state-dir          [& args] (apply loader/state-dir args))

;; ----- env / cache -----
(defn env                  [& args] (apply loader/env args))
(defn set-env-override!    [& args] (apply loader/set-env-override! args))
(defn clear-env-overrides! [& args] (apply loader/clear-env-overrides! args))
(defn clear-load-cache!    [& args] (apply loader/clear-load-cache! args))

;; ----- resolution -----
(defn resolve-provider          [& args] (apply loader/resolve-provider args))
(defn resolve-crew              [& args] (apply loader/resolve-crew args))
(defn resolve-crew-context      [& args] (apply loader/resolve-crew-context args))
(defn resolve-history-retention [& args] (apply loader/resolve-history-retention args))
(defn parse-model-ref           [& args] (apply loader/parse-model-ref args))
(defn server-config             [& args] (apply loader/server-config args))
(defn resolve-workspace         [& args] (apply loader/resolve-workspace args))
(defn read-workspace-file       [& args] (apply loader/read-workspace-file args))

(def default-history-retention loader/default-history-retention)

;; ----- install (config -> nexus) -----
(defn install!          [& args] (apply install/install! args))
(defn load-and-install! [& args] (apply install/load-and-install! args))

;; ----- mutation -----
(defn set-config   [& args] (apply mutate/set-config args))
(defn unset-config [& args] (apply mutate/unset-config args))
