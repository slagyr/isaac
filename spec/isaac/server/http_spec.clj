(ns isaac.server.http-spec
  (:require
    [isaac.server.http :as sut]
    [speclj.core :refer :all]))

(describe "HTTP handler"

  (it "creates a handler function"
    (let [handler (sut/create-handler)]
      (should (fn? handler))))

  (it "handler responds to GET /status"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/status"})]
      (should= 200 (:status response))))

  (it "handler returns 404 for unknown routes"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/nope"})]
      (should= 404 (:status response))))

  )
