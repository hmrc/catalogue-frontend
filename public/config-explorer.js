/*
 * Config Explorer page behaviour.
 *
 * Handles grid sizing, side panels, environment columns, difference
 * filtering, display controls, config searching, popovers, URL state,
 * and hash navigation.
 */

function ready(fn) {
    if (
        document.attachEvent
            ? document.readyState === "complete"
            : document.readyState !== "loading"
    ) {
        fn();
    } else {
        document.addEventListener("DOMContentLoaded", fn);
    }
}

ready(function () {
    // -------------------------------------------------------------------------
    // Page state and shared elements
    // -------------------------------------------------------------------------

    const body = document.querySelector("body");
    const onloadParams = new URLSearchParams(
        window.location.search
    );

    // -------------------------------------------------------------------------
    // URL query parameters
    // -------------------------------------------------------------------------

    function addQueryParam(queryParamName, queryParamValue) {
        const url = new URL(window.location);

        url.searchParams.set(
            queryParamName,
            queryParamValue
        );

        window.history.pushState(
            {},
            document.title,
            url
        );
    }

    function setCheckboxWithQueryParam(
        inputElement,
        queryParamName
    ) {
        if (
            onloadParams.has(queryParamName) &&
            onloadParams.get(queryParamName) !==
                inputElement.value.toString()
        ) {
            inputElement.checked =
                onloadParams.get(queryParamName) === "true";

            inputElement.dispatchEvent(
                new Event("change")
            );
        }
    }

    // -------------------------------------------------------------------------
    // Difference filtering
    // -------------------------------------------------------------------------

    function filterRowsByDifference() {
        const differencesOnly = document.querySelector(
            "#show-differences-only"
        ).checked;

        document
            .querySelectorAll(".config-data-row")
            .forEach(row => {
                if (!differencesOnly) {
                    row.classList.remove("hidden");
                    return;
                }

                const visibleValues = Array.from(
                    row.querySelectorAll(
                        ".config-environment-value:not(.hidden)"
                    )
                ).map(cell =>
                    cell.dataset.effectiveValue
                );

                const allVisibleValuesAreEqual =
                    visibleValues.length >= 2 &&
                    new Set(visibleValues).size === 1;

                row.classList.toggle(
                    "hidden",
                    allVisibleValuesAreEqual
                );
            });
    }

    // -------------------------------------------------------------------------
    // Config key and value search
    // -------------------------------------------------------------------------

    // Search parsing

    function parseConfigSearch(rawInput) {
        const input = rawInput
            .trim()
            .toLowerCase();

        if (input.length === 0) {
            return {
                active: false,
                exact: false,
                text: "",
                terms: []
            };
        }

        const isExactSearch =
            input.length >= 2 &&
            input.startsWith("'") &&
            input.endsWith("'");

        if (isExactSearch) {
            return {
                active: true,
                exact: true,
                text: input
                    .slice(1, -1)
                    .trim(),
                terms: []
            };
        }

        return {
            active: true,
            exact: false,
            text: input,
            terms: input
                .split(/\s+/)
                .filter(term => term.length > 0)
        };
    }

    function readConfigSearchInputs() {
        const keySearch = parseConfigSearch(
            document.querySelector(
                "#config-key-search"
            ).value
        );

        const valueSearch = parseConfigSearch(
            document.querySelector(
                "#config-value-search"
            ).value
        );

        return {
            keySearch,
            valueSearch,
            hasKeySearch: keySearch.active,
            hasValueSearch: valueSearch.active
        };
    }

    // Search matching

    function textMatchesSearch(text, search) {
        if (!search.active) {
            return true;
        }

        const candidate = (text || "")
            .trim()
            .toLowerCase();

        if (search.exact) {
            return candidate === search.text;
        }

        return search.terms.every(term =>
            candidate.includes(term)
        );
    }

    function rowMatchesKey(row, searches) {
        if (!searches.hasKeySearch) {
            return true;
        }

        const keyCell = row.querySelector(
            ".config-key"
        );

        if (!keyCell) {
            return false;
        }

        return textMatchesSearch(
            keyCell.dataset.configKey,
            searches.keySearch
        );
    }

    function rowMatchesValue(row, searches) {
        if (!searches.hasValueSearch) {
            return true;
        }

        // Value searches only consider visible environment columns.
        const visibleValueCells = Array.from(
            row.querySelectorAll(
                ".config-environment-value:not(.hidden)"
            )
        );

        // All partial terms must occur in the same environment value.
        return visibleValueCells.some(cell =>
            textMatchesSearch(
                cell.dataset.effectiveValue,
                searches.valueSearch
            )
        );
    }

    // Search result messages and display

    function searchErrorMessage(rows, searches) {
        const hasMatchingKey =
            !searches.hasKeySearch ||
            rows.some(row =>
                rowMatchesKey(row, searches)
            );

        if (!hasMatchingKey) {
            return "Key not found";
        }

        const hasMatchingValue =
            !searches.hasValueSearch ||
            rows.some(row =>
                rowMatchesValue(row, searches)
            );

        if (!hasMatchingValue) {
            return "Value not found in visible environment columns";
        }

        return "Key with value not found";
    }

    function clearConfigSearch() {
        document.querySelector(
            "#config-key-search"
        ).value = "";

        document.querySelector(
            "#config-value-search"
        ).value = "";

        document.querySelector(
            "#config-search-message"
        ).textContent = "";

        document
            .querySelectorAll(".config-data-row")
            .forEach(row => {
                row.classList.remove(
                    "search-hidden"
                );
            });
    }

    function applyConfigSearch(
        hideAllOnNoMatch = false
    ) {
        const searches = readConfigSearchInputs();

        if (
            !searches.hasKeySearch &&
            !searches.hasValueSearch
        ) {
            clearConfigSearch();
            return;
        }

        const rows = Array.from(
            document.querySelectorAll(
                ".config-data-row"
            )
        );

        const matchingRows = rows.filter(row =>
            rowMatchesKey(row, searches) &&
            rowMatchesValue(row, searches)
        );

        if (matchingRows.length === 0) {
            /*
             * A failed manual search preserves the current results.
             * When visible columns change, stale results that no longer
             * match should be hidden.
             */
            if (hideAllOnNoMatch) {
                rows.forEach(row => {
                    row.classList.add(
                        "search-hidden"
                    );
                });
            }

            document.querySelector(
                "#config-search-message"
            ).textContent = searchErrorMessage(
                rows,
                searches
            );

            return;
        }

        const matchingRowSet = new Set(
            matchingRows
        );

        // Filtering only changes visibility; it preserves the table order.
        rows.forEach(row => {
            row.classList.toggle(
                "search-hidden",
                !matchingRowSet.has(row)
            );
        });

        document.querySelector(
            "#config-search-message"
        ).textContent = "";
    }

    // -------------------------------------------------------------------------
    // Grid sizing
    // -------------------------------------------------------------------------

    Split(
        [
            "#header-0",
            "#header-1",
            "#header-2",
            "#header-3",
            "#header-4",
            "#header-5",
            "#header-6",
            "#header-7"
        ],
        {
            sizes: [
                300,
                500,
                500,
                500,
                500,
                500,
                500,
                500
            ]
        }
    );

    // -------------------------------------------------------------------------
    // Menu and help panels
    // -------------------------------------------------------------------------

    document
        .querySelector(".menu-icon")
        .addEventListener("click", event => {
            event.stopPropagation();

            body.classList.remove("help-open");
            body.classList.toggle("sidebar-open");
        });

    document
        .querySelectorAll(".help-icon")
        .forEach(element => {
            element.addEventListener(
                "click",
                event => {
                    event.stopPropagation();

                    body.classList.remove(
                        "sidebar-open"
                    );

                    body.classList.toggle(
                        "help-open"
                    );
                }
            );
        });

    document
        .querySelectorAll("aside")
        .forEach(element => {
            element.addEventListener(
                "click",
                event => {
                    event.stopPropagation();
                }
            );
        });

    document.body.addEventListener(
        "click",
        () => {
            body.classList.remove("sidebar-open");
            body.classList.remove("help-open");
        }
    );

    // -------------------------------------------------------------------------
    // Environment column visibility
    // -------------------------------------------------------------------------

    Array.from(
        document.querySelectorAll(".column-filter")
    ).forEach(input => {
        input.addEventListener(
            "change",
            event => {
                addQueryParam(
                    input.id,
                    event.target.checked
                );

                const columnClass = input.id.replace(
                    "show-",
                    "column-"
                );

                Array.from(
                    document.getElementsByClassName(
                        columnClass
                    )
                ).forEach(item => {
                    item.classList.toggle(
                        "hidden",
                        !event.target.checked
                    );
                });

                /*
                 * Difference filtering and value searching both depend
                 * on which environment columns are currently visible.
                 */
                filterRowsByDifference();

                const searches =
                    readConfigSearchInputs();

                if (searches.hasValueSearch) {
                    applyConfigSearch(true);
                }
            }
        );

        setCheckboxWithQueryParam(
            input,
            input.id
        );
    });

    // -------------------------------------------------------------------------
    // Difference filter control
    // -------------------------------------------------------------------------

    const differencesOnlyInput =
        document.querySelector(
            "#show-differences-only"
        );

    differencesOnlyInput.addEventListener(
        "change",
        event => {
            addQueryParam(
                "show-differences-only",
                event.target.checked
            );

            filterRowsByDifference();
        }
    );

    setCheckboxWithQueryParam(
        differencesOnlyInput,
        "show-differences-only"
    );

    // -------------------------------------------------------------------------
    // Value display controls
    // -------------------------------------------------------------------------

    document
        .querySelector("#show-referenceConf")
        .addEventListener(
            "change",
            event => {
                addQueryParam(
                    "showReferenceConf",
                    event.target.checked
                );

                body.classList.toggle(
                    "hide-refconf-only"
                );
            }
        );

    const deploymentConfInput =
        document.querySelector(
            "#show-deploymentConf"
        );

    deploymentConfInput.addEventListener(
        "change",
        event => {
            addQueryParam(
                "show-deploymentConf",
                event.target.checked
            );

            body.classList.toggle(
                "hide-deploymentconf"
            );
        }
    );

    setCheckboxWithQueryParam(
        deploymentConfInput,
        "show-deploymentConf"
    );

    const evictionsInput =
        document.querySelector(
            "#show-evictions"
        );

    evictionsInput.addEventListener(
        "change",
        event => {
            addQueryParam(
                "show-evictions",
                event.target.checked
            );

            Array.from(
                document.querySelectorAll(
                    ".strikethrough"
                )
            ).forEach(item => {
                item.classList.toggle(
                    "hide-evictions"
                );
            });
        }
    );

    setCheckboxWithQueryParam(
        evictionsInput,
        "show-evictions"
    );

    const wordWrapInput =
        document.querySelector("#word-wrap");

    wordWrapInput.addEventListener(
        "change",
        event => {
            addQueryParam(
                "word-wrap",
                event.target.checked
            );

            body.classList.toggle("word-wrap");
        }
    );

    setCheckboxWithQueryParam(
        wordWrapInput,
        "word-wrap"
    );

    /*
     * show-warnings and show-referenceConf are initially handled
     * server-side, so their initial state is not reapplied here.
     */
    document
        .querySelector("#show-warnings")
        .addEventListener(
            "change",
            event => {
                addQueryParam(
                    "showWarnings",
                    event.target.checked
                );

                body.classList.toggle(
                    "show-warnings"
                );
            }
        );

    // -------------------------------------------------------------------------
    // Search event handlers
    // -------------------------------------------------------------------------

    document
        .querySelectorAll(
            "#config-key-search, #config-value-search"
        )
        .forEach(input => {
            input.addEventListener(
                "keydown",
                event => {
                    if (event.key === "Enter") {
                        event.preventDefault();
                        applyConfigSearch();
                    }
                }
            );
        });

    document
        .querySelector("#apply-config-search")
        .addEventListener(
            "click",
            () => {
                applyConfigSearch();
            }
        );

    document
        .querySelector("#clear-config-search")
        .addEventListener(
            "click",
            clearConfigSearch
        );

    // -------------------------------------------------------------------------
    // Bootstrap popovers
    // -------------------------------------------------------------------------

    const popoverTriggerList =
        document.querySelectorAll(
            '[data-bs-toggle="popover"]'
        );

    [...popoverTriggerList].forEach(
        popoverTriggerElement => {
            new bootstrap.Popover(
                popoverTriggerElement
            );
        }
    );

    // -------------------------------------------------------------------------
    // Hash navigation and row highlighting
    // -------------------------------------------------------------------------

    window.onhashchange = function () {
        if (!window.location.hash) {
            return;
        }

        /*
         * Decode config keys because they may contain characters such
         * as the quotation marks in logger."uk.hmrc".
         */
        const rowId = decodeURI(
            window.location.hash.replace(
                "#",
                "config-row-"
            )
        );

        const row = document.getElementById(
            rowId
        );

        Array.from(
            document.getElementsByClassName(
                "highlight-amber"
            )
        ).forEach(element => {
            element.classList.remove(
                "highlight-amber"
            );
        });

        if (row) {
            row.scrollIntoView({
                block: "center",
                inline: "nearest",
                behavior: "smooth"
            });

            row.classList.add(
                "highlight-amber"
            );
        }
    };

    // Apply hash navigation when the page initially loads.
    window.onhashchange();
});
