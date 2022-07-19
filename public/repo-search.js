let repoInput = document.getElementById("search")
let repoMatches = document.getElementById("matches")

const minSearchLength = 2

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
        oReq.open("GET", "/repositories-search?query=" + encodeURIComponent(q));
        oReq.send();
    }, 225)

}
