let repoInput = document.getElementById("search")
let repoMatches = document.getElementById("matches")
let teamFilter = document.getElementById("team-filter").value
let repoTypeFilter = document.getElementById("type-search").value

let allButtons = document.getElementsByClassName("sort")
let currentColumnSorted = "name"
let currentSortOrder = "asc"

document.getElementById("repo-table-headings").addEventListener("click", function(e) {
    let selected = e.target
    if(selected.nodeName === "BUTTON") {
        updateSelectedColumn(selected)
        removeOtherColumnSorting(selected)
        let order = selected.classList.contains("asc") ? "asc" : "desc"

        //Set the current sorting configuration so it will be respected by repoSearch
        currentColumnSorted = selected.id
        currentSortOrder = order

        sortByColumn(selected.id, order)
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

function sortByColumn(column, order) {
    let oReq = new XMLHttpRequest();
    oReq.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            repoMatches.innerHTML = oReq.responseText;
        }
    };

    let orderQueryParam = "&sortOrder=" + encodeURIComponent(order)
    let searchQueryParam = "&name=" + encodeURIComponent(repoInput.value)
    let teamQueryParam     = (teamFilter === "")     ? "" : "&team=" + encodeURIComponent(teamFilter)
    let repoTypeQueryParam = (repoTypeFilter === "") ? "" : "&repoType=" + encodeURIComponent(repoTypeFilter)

    oReq.open("GET", "/repositories-search?column=" + encodeURIComponent(column) +
        orderQueryParam +
        searchQueryParam +
        teamQueryParam +
        repoTypeQueryParam
    )
    oReq.send()
}


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
            if (this.readyState == 4 && this.status == 200) {
                repoMatches.innerHTML = oReq.responseText;
            }
        };

        let teamQueryParam     = (teamFilter == "")     ? "" : "&team=" + encodeURIComponent(teamFilter)
        let repoTypeQueryParam = (repoTypeFilter == "") ? "" : "&repoType=" + encodeURIComponent(repoTypeFilter)
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
