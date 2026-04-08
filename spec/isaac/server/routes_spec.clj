(ns isaac.server.routes-spec
  (:require
    [isaac.server.routes :as sut]
    [speclj.core :refer :all]))

(describe "Routes"

  (it "routes GET /status to status handler"
    (let [response (sut/handler {:request-method :get :uri "/status"})]
      (should= 200 (:status response))))

  (it "returns 404 for unknown paths"
    (let [response (sut/handler {:request-method :get :uri "/unknown"})]
      (should= 404 (:status response))))

  (it "returns 404 for unknown methods on known paths"
    (let [response (sut/handler {:request-method :post :uri "/status"})]
      (should= 404 (:status response))))

  )
