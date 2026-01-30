(ns mdm.isaac.server.http-spec
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.server.http :as sut]
            [mdm.isaac.server.session :as session]
            [c3kit.apron.corec :as ccc]
            [clojure.string :as str]
            [ring.middleware.session.store :as store]
            [speclj.core :refer :all])
  (:import (java.net URLDecoder)))

(describe "http"

  (redefs-around [config/development? false])

  (it "flash"
    (with-redefs [sut/web-handler (fn [_req] {:flash "some-flash" :status 200 :body "foo"})]
      (let [response          ((sut/root-handler) {:uri "/"})
            cookies           (get-in response [:headers "Set-Cookie"])
            cookie-assignment (str session/cookie-name "=")
            session-cookie    (ccc/ffilter #(str/starts-with? % cookie-assignment) cookies)
            cookie-store      (:store session/config)]
        (should-not-be-nil session-cookie)
        (let [data (-> session-cookie
                       (str/split #";")
                       first
                       (subs (count cookie-assignment))
                       (URLDecoder/decode "UTF-8"))]
          (should= {:_flash "some-flash"} (store/read-session cookie-store data))))))

  ;(it "wrap-transit"
  ;  (with-redefs [sut/web-handler (fn [req] {:status 200 :body (:params req)})]
  ;    (let [response (sut/root-handler {:uri     "/"
  ;                                        :headers {"content-type" "application/transit+json"}
  ;                                        :body    (util/->transit {:foo "bar"})})]
  ;      (should= {:foo "bar"} (:body response)))))

  )
