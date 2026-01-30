(ns mdm.isaac.robots
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helperc]
            #?(:cljs [reagent.core :as reagent])
            [mdm.isaac.thought.schema :as thought.schema]
            [mdm.isaac.user.schema :as user.schema]
            [mdm.isaac.user.core :as user]
            #?(:clj [mdm.isaac.spec-helper :as helper])
            [speclj.core :refer [before after]]))

(def schemas [user.schema/all
              thought.schema/all
              ])

(deftype Entity [atm kind]
  ;; MDM - An Entity is reloaded from the database each time is de-referenced (@).
  ;; It's super convenient for test code.
  #?(:clj clojure.lang.IDeref :cljs cljs.core/IDeref)
  (#?(:clj deref :cljs -deref) [_]
    (if @atm
      (db/reload @atm)
      (log/warn (str "Using nil entity.  Maybe add (with-kinds " (or kind "<kind") ")")))))

(defn e-atom [entity] #?(:clj (.atm entity) :cljs (.-atm entity)))
(defn entity [kind] (Entity. (atom nil) kind))

(def initialized-entities (atom []))

(defn clear-entities! []
  (doseq [entity @initialized-entities]
    (reset! (e-atom entity) nil))
  (reset! initialized-entities []))

(defn init-entity! [entity & opt-args]
  (let [values (ccc/->options opt-args)]
    (reset! (e-atom entity) (db/tx values))
    (swap! initialized-entities conj entity)))

(def robbie (entity :user))
(def speedy (entity :user))

(defmulti -init-kind! identity)

(defmethod -init-kind! :user [_]
  #?(:clj (when-not (= user/hash-password helper/speedy-hash)
            (log/report "SLOW PASSWORD HASH! Add (helper/with-fast-password-hash) to your spec")))
  (init-entity! robbie (db/tx :kind :user
                              :name "Robbie RB-76"
                              :email "robbie@isaac.com"
                              :password #?(:clj (user/hash-password "nursemaid") :cljs nil)))
  (init-entity! speedy (db/tx :kind :user
                              :name "Speedy SPD-13"
                              :email "speedy@isaac.com"
                              :password #?(:clj (user/hash-password "runaround") :cljs nil))))

(def sky-blue (entity :thought))

(defmethod -init-kind! :thought [_]
  (init-entity! sky-blue (db/tx :kind :thought
                                :content "The sky is blue")))

(def deps
  ;; Add entities here with a list of entities they depend on (shallow).
  {:user    []
   :thought []
   :all     [:user :thought]})

(defmethod -init-kind! :all [_])

(def initialized-kinds (atom #{}))

(defn- maybe-init-kind! [kind]
  (when-not (contains? @initialized-kinds kind)
    (-init-kind! kind)
    (swap! initialized-kinds conj kind)))

(defn init! [& kinds]
  (assert (seq kinds))
  (loop [kinds kinds]
    (if-let [kind (first kinds)]
      (if-let [reqs (seq (remove @initialized-kinds (get deps kind)))]
        (recur (concat reqs kinds))
        (do
          (maybe-init-kind! kind)
          (recur (rest kinds))))
      @initialized-kinds)))

(def memory-config {:impl :memory :store #?(:clj (atom nil) :cljs (reagent/atom nil))})

(defn with-kinds
  ([] (apply with-kinds (:all deps)))
  ([& kinds]
   (list
     ;; TODO - MDM: instead of using schemas (all schemas), use legend to pull in only those specified by kinds.
     (helperc/with-schemas memory-config schemas)
     #?(:clj (helper/with-fast-password-hash))
     (before (reset! initialized-kinds #{})
             (apply init! kinds))
     (after (clear-entities!)))))
