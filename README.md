# meowallet-clj

A Clojure library designed to interface with MEO Wallet (https://wallet.pt). 
Wraps the full Procheckout functionallity including:
 * start checkout
 * get and filter operations
 * receive callbacks
 * refunds

For more information on the MEO Wallet base concepts visit the https://developers.wallet.pt. The maps returned by meowallet-clj functions are directly mapped from the MEO Wallet API responses.

## Usage

A basic example is
```
(let [{:keys "id" "checkout_url"} (with-key-on :sandbox "aff23rf342rf324f23f4" start-checkout {"amount" 10})
```

Public functions are
 * with-key-on
 * map-checkout
 * start-checkout
 * register-callback
 * get-operation
 * get-operations
 * get-operations-seq
 * get-operations-invoice
 * refund

Refer to functions doc and to the MEO Wallet developers site. You can find usage examples on the tests and on the mw-store-clj project.


## License

Copyright © Carlos Morgado

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
