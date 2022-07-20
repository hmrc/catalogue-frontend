let repoInput = document.getElementById("search")
let repoMatches = document.getElementById("matches")
let teamFilter = document.getElementById("team-filter").value
let repoTypeFilter = document.getElementById("type-search").value


const minSearchLength = 3

repoInput.onkeyup = e => {
    if (e.target.value.length >= minSearchLength) {
        repoSearch(e.target.value)
    }
}

// Prevent search firing off until user stops typing
let repoDebounceTimer

function repoSearch(q) {
    clearTimeout(repoDebounceTimer)
    repoDebounceTimer = setTimeout(() => {
        let oReq = new XMLHttpRequest();
        oReq.onreadystatechange = function () {
            if (this.readyState == 4 && this.status == 200) {
                repoMatches.innerHTML = oReq.responseText;
                selectedItem = 0
                highlight(0)
            }
        };

        let teamQueryParam     = (teamFilter == "")     ? "" : "&team=" + encodeURIComponent(teamFilter)
        let repoTypeQueryParam = (repoTypeFilter == "") ? "" : "&repoType=" + encodeURIComponent(repoTypeFilter)

        oReq.open("GET", "/repositories-search?name=" + encodeURIComponent(q) + teamQueryParam + repoTypeQueryParam)
        oReq.send();
    }, 225)

}
