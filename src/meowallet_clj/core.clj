(ns meowallet-clj.core
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as client])
(require '[cheshire.core :as json])
(require '[ring.util.request :as ring-req])

(defmacro def- 
  [symbol & body] 
  `(def ^:private ~symbol ~@body))



(defn with-key-on [env key]
  "Sets up the environemnt for other functions.
  env can be :production or :sandbox. key is your MEO Wallet API key"
  (let [baseurl (if (= env :production)
                  "https://services.wallet.pt/api/v2/" 
                  "https://services.sandbox.meowallet.pt/api/v2/")]
      (fn [f & body] 
        (apply f baseurl key body))
    ))

(defn map-checkout 
  "Takes a simple flat map of common checkout atributes and returns a properly formated checkout structure.
  Caveat: does not know all the checkout attributes"
  [{:keys [url_confirm url_cancel amount currency client items] :or {currency "EUR"}}]
  {:pre [(some? amount)
         (= currency "EUR")]}
  (merge {"payment" (merge {"amount" amount "currency" currency} 
                          (if (map? client) {"client" client})
                          (if (vector? items) {"items" items})
                          )}
         (if (some? url_confirm) {"url_confirm" url_confirm})
         (if (some? url_cancel) {"url_confirm" url_cancel}) )
  )

(defn- mw-parse-response 
  [resp]
  (try+
    (json/parse-string (resp :body))
    (catch Object e
      nil)
    )
  )

(defn- mw-call
  [method base-url k rsc body query]
  (try+
    (let [encodedbody (if (map? body)
              (json/generate-string body)
              body
              )]
      (client/request { ;;;:debug true
                       :method method 
                       :url (str base-url rsc)
                       :headers {"authorization" (str "WalletPT " k)}
                       :content-type :json
                       :body encodedbody 
                       :query-params query}))
    (catch Object e
      ;;; TODO something more useful here
      (println (str "mw call exception " e))
;;;      (throw e)
      ))
    )

(defn- mw-post-
  [base-url k rsc body]
  (mw-call :post base-url k rsc body {}))

(defn- mw-post
  [base-url k rsc body]
  (let [resp (mw-call :post base-url k rsc body {})]
    (mw-parse-response resp)
    )
  )

(defn- mw-get
  [base-url k rsc params]
  (let [resp (mw-call :get base-url k rsc "" params)]
    (mw-parse-response resp)))


(defn start-checkout 
  "Receives a map of checkout attributes and returns a MEO Wallet checkout map.
  See https://developers.wallet.pt/en/procheckout/structures.html#checkout for information on the attributes, sample-checkout on the tests and map-checkout for utility on checkout creation."
  [base-url k params]
  (let [resp (mw-post base-url k "checkout" params)]
    resp
  )
)


(defn- callback-valid? [base-url k thebody]
  (let [resp (mw-post- base-url k "callback/verify" thebody)]
    (if (map? resp)
      (= (resp :status) 200)
      nil
    )
  ))

(def- bad-req-response
  {:status 400
   :header {:content-type "text/plain"}
   :body "bad request"}
  )

(def- good-req-response
  {:status 200
   :header {:content-type "text/plain"}
   :body "OK"}
  )

(defn register-callback
  "Takes a payment callback function and returns a POST ring handler you can install on the route you desire.
  Callback function is called with the callback map (https://developers.wallet.pt/en/procheckout/callbacks.html) and should return a true value to signal the callback was processed
  NOTE the callback function is called in line so it should be brief in execution to avoid response timeouts."
  [base-url k cb]
  (fn [{:keys [headers body request-method] :as request}]
    (let [bodystr (ring-req/body-string request)]
       (if-not (and (= request-method :post) (= (headers "content-type") "application/json"))
         bad-req-response 
         (if (callback-valid? base-url k bodystr)
           (if (cb (json/parse-string bodystr))
             good-req-response
             bad-req-response
           ))))))

(defn get-operation
  "Get an operation by id"
  [base-url k id]
  {:pre [(string? id)]}
  (mw-get base-url k (str "operations/" id) nil))

(defn get-operations
  "Gets a list of operations for this wallet. Takes a map of filter parameters as described in https://developers.wallet.pt/en/procheckout/resources.html#api_v2_operations"
  ([base-url k] (mw-get base-url k "operations" {}))
  ([base-url k query] (mw-get base-url k "operations" query))
  )

(defn- get-operations-seq-inner
  [base-url k page]
  (let [b (chunk-buffer 10)
        res (get-operations base-url k {"offset" (* page 10) "limit" 10})]
    (if-not (and (map? res) (contains? res "elements") (not (empty? (res "elements"))))
      nil
      (loop [r (res "elements")]
        (if (empty? r)
          (chunk b)
          (do (chunk-append b (first r))
              (recur (rest r))))))))

(defn get-operations-seq
  "Returns a sequence of operations for this wallet"
  ([base-url k] (get-operations-seq base-url k 0))
  ([base-url k page] 
   (lazy-seq 
     (let [c (get-operations-seq-inner base-url k page)]
      (chunk-cons c (when (some? c) (get-operations-seq base-url k (inc page))))))))

(defn get-operations-invoice
  "Takes a ext_invoiceid and returns a vector of operations for an invoiceid"
  [base-url k invoiceid]
  {:pre [(string? invoiceid)]}
  (mw-get base-url k (str "operations/?ext_invoiceid=" invoiceid)))

(defn refund
  "Refund some amount of an operation. Takes an operation map (from get-operation), the amount to refund and a map of ext_* parameters"
  [base-url k operation amount extparams]
  {:pre [(map? operation)
         (contains? operation "id")
         (contains? operation "amount")
         (contains? operation "refundable")]}
  (if-not (operation "refundable")
    nil
    (if (> amount (operation "amount"))
      nil
      (mw-post base-url k (str "operations/" (operation "id") "/refund") 
               (merge (if (= amount (operation "amount"))
                        {"type" "full"}
                        {"type" "partial"
                         "amount" amount}) 
                      extparams))
      ))
  )
