/*
 * Config Explorer page behaviour.
 *
 * Handles grid sizing, side panels, environment columns, difference
 * filtering, display controls, config searching, copied URL state,
 * popovers, and hash navigation.
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
    const onloadParams = new URLSearchParams(window.location.search);

    const configKeySearchQueryParam = "configKeySearch";
    const configValueSearchQueryParam = "configValueSearch";

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
        if (!onloadParams.has(queryParamName)) {
            return;
        }

        const checked =
            onloadParams.get(queryParamName) === "true";

        if (inputElement.checked === checked) {
            return;
        }

        inputElement.checked = checked;
        inputElement.dispatchEvent(
            new Event("change")
        );
    }

    function setOptionalQueryParam(
        url,
        queryParamName,
        queryParamValue
    ) {
        if (queryParamValue.trim().length === 0) {
            url.searchParams.delete(queryParamName);
            return;
        }

        url.searchParams.set(
            queryParamName,
            queryParamValue
        );
    }

    function addConfigSearchToUrl(url) {
        setOptionalQueryParam(
            url,
            configKeySearchQueryParam,
            document.querySelector(
                "#config-key-search"
            ).value
        );

        setOptionalQueryParam(
            url,
            configValueSearchQueryParam,
            document.querySelector(
                "#config-value-search"
            ).value
        );
    }

    function addConfigExplorerControlsToUrl(url) {
        const checkboxQueryParams = [
            [
                "#show-referenceConf",
                "showReferenceConf"
            ],
            [
                "#show-deploymentConf",
                "show-deploymentConf"
            ],
            [
                "#show-evictions",
                "show-evictions"
            ],
            [
                "#word-wrap",
                "word-wrap"
            ],
            [
                "#show-warnings",
                "showWarnings"
            ],
            [
                "#show-differences-only",
                "show-differences-only"
            ]
        ];

        checkboxQueryParams.forEach(
            ([selector, queryParamName]) => {
                const checkbox =
                    document.querySelector(selector);

                url.searchParams.set(
                    queryParamName,
                    checkbox.checked
                );
            }
        );

        document
            .querySelectorAll(".column-filter")
            .forEach(checkbox => {
                url.searchParams.set(
                    checkbox.id,
                    checkbox.checked
                );
            });

        addConfigSearchToUrl(url);
    }

    function updateConfigSearchUrl() {
        const url = new URL(window.location);

        addConfigSearchToUrl(url);

        window.history.replaceState(
            {},
            document.title,
            url
        );
    }

    // -------------------------------------------------------------------------
    // Difference filtering
    // -------------------------------------------------------------------------

    function filterRowsByDifference() {
        const differencesOnly =
            document.querySelector(
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

        // All partial terms must occur in one environment value.
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

        document.querySelector(
            "#config-copy-message"
        ).textContent = "";

        document
            .querySelectorAll(".config-data-row")
            .forEach(row => {
                row.classList.remove(
                    "search-hidden"
                );
            });

        updateConfigSearchUrl();
    }

    function applyConfigSearch(
        hideAllOnNoMatch = false
    ) {
        updateConfigSearchUrl();

        const searches =
            readConfigSearchInputs();

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
             * When visible columns change, stale results which no longer
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

        const matchingRowSet =
            new Set(matchingRows);

        // Filtering changes visibility without changing table order.
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

    function restoreConfigSearchFromQueryParams() {
        const keySearch = onloadParams.get(
            configKeySearchQueryParam
        );

        const valueSearch = onloadParams.get(
            configValueSearchQueryParam
        );

        if (keySearch !== null) {
            document.querySelector(
                "#config-key-search"
            ).value = keySearch;
        }

        if (valueSearch !== null) {
            document.querySelector(
                "#config-value-search"
            ).value = valueSearch;
        }

        const searches =
            readConfigSearchInputs();

        if (
            searches.hasKeySearch ||
            searches.hasValueSearch
        ) {
            applyConfigSearch();
        }
    }

    // -------------------------------------------------------------------------
    // Copy Config Explorer URL
    // -------------------------------------------------------------------------

    async function copyConfigExplorerUrl() {
        const url = new URL(window.location);

        /*
         * Read the current controls directly instead of relying on the
         * address bar to already contain every current setting.
         */
        addConfigExplorerControlsToUrl(url);

        const copyMessage =
            document.querySelector(
                "#config-copy-message"
            );

        try {
            await navigator.clipboard.writeText(
                url.toString()
            );

            copyMessage.textContent = "URL copied";
        } catch (error) {
            copyMessage.textContent =
                "Unable to copy URL";
        }
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
            body.classList.remove(
                "sidebar-open"
            );

            body.classList.remove(
                "help-open"
            );
        }
    );

    // -------------------------------------------------------------------------
    // Environment column visibility
    // -------------------------------------------------------------------------

    document
        .querySelectorAll(".column-filter")
        .forEach(input => {
            input.addEventListener(
                "change",
                event => {
                    addQueryParam(
                        input.id,
                        event.target.checked
                    );

                    const columnClass =
                        input.id.replace(
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
                     * on the currently visible environment columns.
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

    /*
     * showReferenceConf is initially handled server-side because changing it
     * affects the content rendered by the controller.
     */

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

            document
                .querySelectorAll(".strikethrough")
                .forEach(item => {
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

    /*
     * showWarnings is initially handled server-side through the rendered
     * body class, so it is not reapplied with setCheckboxWithQueryParam.
     */

    // -------------------------------------------------------------------------
    // Search and Copy URL event handlers
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
        .querySelector(
            "#copy-config-explorer-url"
        )
        .addEventListener(
            "click",
            copyConfigExplorerUrl
        );

    /*
     * Column and checkbox state has been restored above. Restore the search
     * afterwards because value searches depend on visible environments.
     */
    restoreConfigSearchFromQueryParams();

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
         * Decode config keys because they may contain characters such as
         * the quotation marks in logger."uk.hmrc".
         */
        const rowId = decodeURI(
            window.location.hash.replace(
                "#",
                "config-row-"
            )
        );

        const row =
            document.getElementById(rowId);

        document
            .querySelectorAll(".highlight-amber")
            .forEach(element => {
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
