let repoInput = document.getElementById("search")
let repoMatches = document.getElementById("matches")
let allButtons = document.getElementsByClassName("sort")

let currentColumnSorted = "name"
let currentSortOrder = "asc"

//Respect any Query String Params
const params = new Proxy(new URLSearchParams(window.location.search), {
    get: (searchParams, prop) => searchParams.get(prop),
});

repoInput.value = params.name

if(params.team !== null) {
    document.getElementById("team-filter").value = (params.team === "" ? "All" : params.team)
}
if(params.repoType !== null) {
    document.getElementById("type-search").value = (params.repoType === "" ? "All" : params.repoType)
}
//Filters Listeners

document.getElementById("repos-form-row").addEventListener("change", function (e) {
    applyFilters()
})

function applyFilters() {
    let oReq = new XMLHttpRequest();
    oReq.onreadystatechange = function () {
        if (this.readyState === 4 && this.status == 200) {
            repoMatches.innerHTML = oReq.responseText;
        }
    };

    let teamQueryParam = "&team=" + encodeURIComponent(getTeamFilter())
    let repoTypeQueryParam ="&repoType=" + encodeURIComponent(getRepoTypeFilter())
    let columnQueryParam = "&column=" + encodeURIComponent(currentColumnSorted)
    let orderQueryParam = "&sortOrder=" + encodeURIComponent(currentSortOrder)

    oReq.open("GET",
        "/repositories-search?name=" + encodeURIComponent(getSearchInputValue())
        + teamQueryParam
        + repoTypeQueryParam
        + columnQueryParam
        + orderQueryParam)

    oReq.send();
}

//Sorting Listener
document.getElementById("repo-table-headings").addEventListener("click", function(e) {
    let selected = e.target
    if(selected.nodeName === "BUTTON") {
        let order = selected.classList.contains("asc") ? "asc" : "desc"

        //Set the current sorting configuration so it will be respected by repoSearch
        currentColumnSorted = selected.id
        currentSortOrder = order

        sortByColumn(selected, selected.id, order)
    }
})

function removeOtherColumnSorting(selected){
    for(let i = 0; i < allButtons.length; i++) {
        if(allButtons[i].id === selected.id) {
            continue
        } else {
            allButtons[i].classList.remove("asc", "desc")
        }
    }
}

function updateSelectedColumn(selected) {
    if(selected.classList.contains("asc")){
        selected.classList.remove("asc")
        selected.classList.add("desc")
    } else {
        selected.classList.remove("desc")
        selected.classList.add("asc")
    }
}

function sortByColumn(selectedCol, columnId, order) {
    let oReq = new XMLHttpRequest();
    oReq.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            repoMatches.innerHTML = oReq.responseText;
            updateSelectedColumn(selectedCol)
            removeOtherColumnSorting(selectedCol)
        }
    };

    let orderQueryParam = "&sortOrder=" + encodeURIComponent(order)
    let searchQueryParam = "&name=" + encodeURIComponent(getSearchInputValue())
    let teamQueryParam     = "&team=" + encodeURIComponent(getTeamFilter())
    let repoTypeQueryParam = "&repoType=" + encodeURIComponent(getRepoTypeFilter())

    oReq.open("GET", "/repositories-search?column=" + encodeURIComponent(columnId) +
        orderQueryParam +
        searchQueryParam +
        teamQueryParam +
        repoTypeQueryParam
    )
    oReq.send()
}

//Dynamic Search Listener
const minSearchLength = 3

repoInput.onkeyup = e => {
    if (e.target.value.length >= minSearchLength) {
        repoSearch(e.target.value)
    }
}

// Prevent search firing off until user stops typing
let repoDebounceTimer

//Search function
function repoSearch(searchTerm = "", ) {
    clearTimeout(repoDebounceTimer)
    repoDebounceTimer = setTimeout(() => {
        let oReq = new XMLHttpRequest();
        oReq.onreadystatechange = function () {
            if (this.readyState === 4 && this.status == 200) {
                repoMatches.innerHTML = oReq.responseText;
            }
        };

        let teamQueryParam     = "&team=" + encodeURIComponent(getTeamFilter())
        let repoTypeQueryParam = "&repoType=" + encodeURIComponent(getRepoTypeFilter())
        let columnQueryParam = "&column=" + encodeURIComponent(currentColumnSorted)
        let orderQueryParam = "&sortOrder=" + encodeURIComponent(currentSortOrder)

        oReq.open("GET",
            "/repositories-search?name=" + encodeURIComponent(searchTerm)
            + teamQueryParam
            + repoTypeQueryParam
            + columnQueryParam
            + orderQueryParam)
        oReq.send();
    }, 225)

}

function getTeamFilter() { return document.getElementById("team-filter").value}
function getRepoTypeFilter() { return  document.getElementById("type-search").value}
function getSearchInputValue() { return document.getElementById("search").value}
