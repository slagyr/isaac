(ns isaac.api
  (:require
    [isaac.comm :as comm-impl]
    [isaac.comm.registry :as comm-registry]
    [isaac.lifecycle :as lifecycle-impl]
    [isaac.provider :as provider-impl]
    [isaac.session.storage :as session-impl]))

(def Comm                comm-impl/Comm)
(def Lifecycle           lifecycle-impl/Lifecycle)
(def register-comm!      comm-registry/register-factory!)
(def comm-registered?    comm-registry/registered?)
(def register-provider!  provider-impl/register!)
(def create-session!     session-impl/create-session!)
(def get-session         session-impl/get-session)

(defn run-turn!
  "Delegates to isaac.drive.turn/run-turn! (resolved lazily to avoid load cycles
   through the LLM provider → api → drive.turn → dispatch → LLM provider chain)."
  [& args]
  (apply (requiring-resolve 'isaac.drive.turn/run-turn!) args))
