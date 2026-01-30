(ns mdm.isaac.server.session-spec
  (:require [mdm.isaac.server.session :as sut]
            [speclj.core :refer :all])
  (:import (ring.middleware.session.cookie CookieStore)))

(describe "Session"

  (it "session-config"
    (should= "isaac-session" (:cookie-name sut/config))
    (should= {:http-only true :secure true} (:cookie-attrs sut/config))
    (should= CookieStore (class (:store sut/config)))
    (should= "The day you stop" (-> (:store sut/config) .-secret-key String.)))

  )
