(ns mdm.isaac.user.schema
  (:require [c3kit.apron.schema :as s]))

(def user
  {:kind                (s/kind :user)
   :id                  s/id
   :email               {:type :string :db [:unique-value]}
   :name                {:type :string}
   :password            {:type :string :present s/omit}
   :confirmation-token  {:type :uuid :present s/omit}
   :recovery-token      {:type :uuid :present s/omit}
   })

(def social-login
  {:kind      (s/kind :social-login)
   :id        s/id
   :provider  {:type :keyword}
   :social-id {:type :string}
   :user-id   {:type :ref}
   })

(def all [user social-login])

