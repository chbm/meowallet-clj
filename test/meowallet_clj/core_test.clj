(ns meowallet-clj.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [meowallet-clj.core :refer :all]))

(def test-key "f0bd99cab8c6fadf7ae414e37667d7c2973cff65")

(def sample-checkout {
   "url_confirm" "http://foo.com"
   "url_cancel" "http://foo.com"
   "payment" {
    "amount" 10 "currency" "EUR"
    "client" {"name" "john" "email" "john@example.com" "phone" "351555121234"}
    "items" [ {"name" "things" "qt" 1 "ref" "sku122134" "amount" 10} ]
    "ext_invoiceid" "a2352342345"
   }
   "exclude" ["CC","MB"]
   "required_fields" { "shipping" false "name" true "email" true "phone" false}
   })

(def callback-body "{\"ext_invoiceid\":null,\"ext_email\":null,\"event\":\"COMPLETED\",\"operation_id\":\"533fde50-c765-44db-b36a-c4ebb8eca591\",\"currency\":\"EUR\",\"ext_customerid\":null,\"amount\":10,\"operation_status\":\"COMPLETED\",\"user\":\"16\",\"method\":\"WALLET\"}")

(def test-operation "533fde50-c765-44db-b36a-c4ebb8eca591")

(def for-tests (with-key-on :sandbox "f0bd99cab8c6fadf7ae414e37667d7c2973cff65" ))

(deftest test-start-checkout
  (is (contains? (for-tests start-checkout sample-checkout) "id"))
  )

(defn test-callback-handler
  [operation]
  true)

(deftest test-callback
  (let [process-callback (for-tests register-callback test-callback-handler)
        resp (process-callback (-> (mock/request :post "/")
                                      (mock/content-type "application/json")
                                      (mock/body callback-body)))]
    (is (map? resp))
    (is (= 200 (resp :status)))
    )
  )

(deftest test-get-operation
  (let [resp (for-tests get-operation test-operation)]
    (is (= true (map? resp)))
    (is (= test-operation (resp "id")))
    ))

(deftest test-get-operations
  (let [resp (for-tests get-operations)]
    (is (= true (map? resp)))))

