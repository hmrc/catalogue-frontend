let repoInput = document.getElementById("search")
let repoMatches = document.getElementById("matches")
let allButtons = document.getElementsByClassName("sort")

let currentColumnSorted = "name"
let currentSortOrder = ""

//Respect any Query String Params
const params = new Proxy(new URLSearchParams(window.location.search), {
    get: (searchParams, prop) => searchParams.get(prop),
});

repoInput.value = params.name

if(params.team !== null) {
    document.getElementById("team-filter").value = params.team
}
if(params.repoType !== null) {
    document.getElementById("type-search").value = params.repoType
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

    oReq.open("GET",
        "/repositories-search" +
        "?name=" + encodeURIComponent(getSearchInputValue()) +
        "&team=" + encodeURIComponent(getTeamFilter()) +
        "&repoType=" + encodeURIComponent(getRepoTypeFilter()) +
        "&column=" + encodeURIComponent(currentColumnSorted) +
        "&sortOrder=" + encodeURIComponent(currentSortOrder))

    oReq.send();
}

//Sorting Listener
document.getElementById("repo-table-headings").addEventListener("click", function(e) {
    let selected = e.target
    if(selected.nodeName === "BUTTON") {
        let requestedOrdering = selected.classList.contains("asc") ? "desc" : "asc"

        //Set the current sorting configuration so it will be respected by other listeners
        currentColumnSorted = selected.id
        currentSortOrder = requestedOrdering

        sortByColumn(selected, selected.id, requestedOrdering)
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
    } else if (selected.classList.contains("desc")){
        selected.classList.remove("desc")
        selected.classList.add("asc")
    } else {
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

    oReq.open("GET",
        "/repositories-search" +
        "?column=" + encodeURIComponent(columnId) +
        "&sortOrder=" + encodeURIComponent(order) +
        "&name=" + encodeURIComponent(getSearchInputValue()) +
        "&team=" + encodeURIComponent(getTeamFilter()) +
        "&repoType=" + encodeURIComponent(getRepoTypeFilter())
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

        oReq.open("GET",
            "/repositories-search" +
            "?name=" + encodeURIComponent(searchTerm) +
            "&team=" + encodeURIComponent(getTeamFilter()) +
            "&repoType=" + encodeURIComponent(getRepoTypeFilter()) +
            "&column=" + encodeURIComponent(currentColumnSorted) +
            "&sortOrder=" + encodeURIComponent(currentSortOrder))
        oReq.send();
    }, 225)

}

function getTeamFilter() { return document.getElementById("team-filter").value}
function getRepoTypeFilter() { return  document.getElementById("type-search").value}
function getSearchInputValue() { return document.getElementById("search").value}
