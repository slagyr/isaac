(ns mdm.isaac.init
  (:require
    ;#?(:cljs [reagent.core :as reagent])
    [c3kit.apron.legend :as legend]
    [c3kit.bucket.api :as db]
    [c3kit.bucket.memory]
    [c3kit.wire.api :as api]
    [mdm.isaac.config :as config]
    [mdm.isaac.schema]
    [mdm.isaac.thought.schema :as thought]
    [mdm.isaac.user.schema :as user]
    ))

(defn install-legend! []
  (legend/init! {
                 :user       user/user
                 :thought    thought/thought
                 :db/retract legend/retract
                 }))

;#?(:cljs (defn install-reagent-db-atom! []
;               (db/set-impl! (db/create-db {:impl :memory :store (reagent/atom nil)} schema/full))))

(defn configure-api! []
  (api/configure! #?(:clj {:ws-handlers 'mdm.isaac.server.routes/ws-handlers
                           :version     (api/version-from-js-file (if config/development? "public/cljs/isaac_dev.js" "public/cljs/isaac.js"))
                           :ajax-on-ex  'mdm.isaac.server.errors/ajax-ex-handler}
                     ;:cljs {:redirect-fn core/goto!}
                     ))
  ;#?(:cljs (rest/configure!))
  )
