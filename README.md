# guestbook

generated using Luminus version "3.91"

## Prerequisites

- [Leiningen][1] ^v2.0
- [NodeJS][2] ^v12.22.1

[1]: https://github.com/technomancy/leiningen

[2]: https://nvm.sh

## Backend Development

- To start web services for the application, run:
    ```sh
    lein run
    ```

- Alternatively; Start a REPL...
    ```sh
    lein repl
    ```
    - ... & "(start)" web services for the application.
        ```lisp
        nREPL server started on port 37217 on host 127.0.0.1 - nrepl://127.0.0.1:37217
        REPL-y 0.4.4, nREPL 0.8.3
        Clojure 1.10.1
        OpenJDK 64-Bit Server VM 11.0.11+9
            Docs: (doc function-name-here)
                (find-doc "part-of-name-here")
        Source: (source function-name-here)
        Javadoc: (javadoc java-object-or-class-here)
            Exit: Control+D or (exit) or (quit)
        Results: Stored in vars *1, *2, *3, an exception in *e

        user=> (start)
        ```

## Frontend Development

- install dependencies

    ```sh
    npm install
    ```

- start shadow-cljs in watch mode

    ```sh
    npx shadow-cljs watch app
    ```

- open a shadow-cljs repl

    ```sh
    npx shadow-cljs cljs-repl app
    ```

## License

Copyright © 2021 TM
