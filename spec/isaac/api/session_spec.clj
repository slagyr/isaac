(ns isaac.api.session-spec
  (:require
    [isaac.api.session :as sut]
    [isaac.session.storage :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.session"

  (it "create-session! re-exports isaac.session.storage/create-session!"
    (should= impl/create-session! sut/create-session!))

  (it "get-session re-exports isaac.session.storage/get-session"
    (should= impl/get-session sut/get-session)))
