(ns meowallet-clj.core-test
  (:require [clojure.test :refer :all]
            [meowallet-clj.core :refer :all]))

(def test-key "f0bd99cab8c6fadf7ae414e37667d7c2973cff65")

(def sample-checkout 
  {
   :url_confirm "http://foo.com"
   :url_cancel "http://foo.com"
   :amount 10 :currency "EUR"
   :client {:name "john"}
   :items [ {:name "things" :qt 1} ] 
   })

(def for-tests (with-key-on :sandbox "f0bd99cab8c6fadf7ae414e37667d7c2973cff65" ))



(deftest test-start-checkout
  (contains? (for-tests start-checkout sample-checkout) :id)
  )

