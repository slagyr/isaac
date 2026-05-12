(ns isaac.server.routes-spec
  (:require
    [isaac.comm.acp :as acp]
    [isaac.comm.acp.websocket]
    [isaac.comm.registry :as comm-registry]
    [isaac.module.loader :as module-loader]
    [isaac.server.hooks]
    [isaac.server.routes :as sut]
    [speclj.core :refer :all]))

(defn exact-handler [_request]
  {:status 201 :body "exact"})

(defn opts-handler [opts request]
  {:body [opts request] :status 202})

(describe "Routes"

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (binding [comm-registry/*registry* (atom (comm-registry/fresh-registry))
              sut/*registry*           (atom (sut/fresh-registry))]
      (it)))

  (it "dispatches exact routes registered at runtime"
    (sut/register-route! :get "/bibelot" #'exact-handler)
    (should= {:status 201 :body "exact"}
             (sut/handler {:request-method :get :uri "/bibelot"})))

  (it "passes handler opts to routes registered with :with-opts?"
    (sut/register-route! :post "/thingy" #'opts-handler {:with-opts? true})
    (let [request {:request-method :post :uri "/thingy" :body "payload"}
          opts    {:cfg {:mode :test}}]
      (should= {:status 202 :body [opts request]}
               (sut/handler opts request))))

  (it "registers the ACP websocket route from core manifest activation"
    (with-redefs [isaac.comm.acp.websocket/handler (fn [opts request]
                                                     {:status 299 :body [opts request]})]
      (module-loader/clear-activations!)
      (should-not (sut/route-registered? :get "/acp"))
      (module-loader/activate-core!)
      (should (sut/route-registered? :get "/acp"))
      (let [request {:request-method :get :uri "/acp"}
            opts    {:cfg {:mode :test}}]
        (should= {:status 299 :body [opts request]}
                 (sut/handler opts request)))))

  (it "routes GET /status to status handler"
    (let [response (sut/handler {:request-method :get :uri "/status"})]
      (should= 200 (:status response))))

  (it "keeps /hooks/* as a built-in prefix route"
    (with-redefs [isaac.server.hooks/handler (fn [opts request]
                                               {:status 204 :body [opts request]})]
      (let [request {:request-method :post :uri "/hooks/bibelot"}
            opts    {:cfg {:mode :test}}]
        (should= {:status 204 :body [opts request]}
                 (sut/handler opts request)))))

  (it "returns 404 for unknown paths"
    (let [response (sut/handler {:request-method :get :uri "/unknown"})]
      (should= 404 (:status response))))

  (it "returns 404 for unknown methods on known paths"
    (let [response (sut/handler {:request-method :post :uri "/status"})]
      (should= 404 (:status response))))

  (it "GET /error throws an exception"
    (should-throw (sut/handler {:request-method :get :uri "/error"})))

  )
