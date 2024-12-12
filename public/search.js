// Drop Down Search


let bar = document.getElementById("catalogue-search-bar")
let searchContainer = document.getElementById("catalogue-search-box")
let input = document.getElementById("catalogue-search")
let matches = document.getElementById("catalogue-search-matches")
let mainmenu = document.getElementById("main-menu-bar")

let selectedItem = 0

const minSearchLen = 3

input.onkeyup = e => {
    if (e.target.value.length >= minSearchLen) {
        matches.classList.remove("d-none")
        if (e.keyCode > 46 || e.keyCode === 32) { // exclude arrow keys
            search(e.target.value)
        }
    } else {
        matches.innerHTML = "";
        matches.classList.add("d-none")
    }
}

// Prevent search firing off until user stops typing
let debounceTimer

function search(q) {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
        let oReq = new XMLHttpRequest();
        oReq.onreadystatechange = function () {
            if (this.readyState === 4 /*DONE*/ && this.status === 200) {
                matches.innerHTML = oReq.responseText;
                selectedItem = 0
                highlight(0)
            }
        };
        oReq.open("GET", "/quicksearch?query=" + encodeURIComponent(q));
        oReq.send();
    }, 250)

}

// show and hide the whole search bar
function toggleSearch() {
    if (searchContainer.classList.contains("search-width-initial")) {
        showSearchBar()
    } else {
        hideSearchBar()
    }
}

function showSearchBar() {
    bar.classList.remove("hidden-for-small-screens")
    searchContainer.classList.remove("search-width-initial")
    searchContainer.classList.add("search-width")
    mainmenu.classList.add("d-none")
    input.focus()
    input.value = ""
}

function hideSearchBar() {
    bar.classList.add("hidden-for-small-screens")
    searchContainer.classList.remove("search-width")
    searchContainer.classList.add("search-width-initial")
    matches.classList.add("d-none")
    mainmenu.classList.remove("d-none")
    input.value = ""
    selectedItem = -1
    matches.innerHTML = ""
}

// hides the drop down part of the search
function clearAutoComplete() {
    matches.classList.add("d-none")
    input.value = ""
    selectedItem = -1
    matches.innerHTML = ""
}

function globalSearchShortcut(e) {
    if (e.ctrlKey && e.key === " ") {
        toggleSearch();
    }
}

function searchInputListener(e) {
    if (e.keyCode === 13) { // enter opens selected item
        // get the first element from the list
        let firstItem = document.getElementById("search-item-" + selectedItem)
        if (firstItem != null) {
            firstItem.click();
        }
    } else if (e.keyCode === 38) { // item select, move up arrow
        if (selectedItem > 0) {
            unhighlight(selectedItem)
            selectedItem--
            highlight(selectedItem)
        }
    } else if (e.keyCode === 40) { // item select move down arrow
        unhighlight(selectedItem)
        selectedItem++
        highlight(selectedItem)
    } else if (e.keyCode === 27) { // escape
        hideSearchBar()
    } else if (e.keyCode > 40 || e.keyCode < 33) { // trigger search
        if (e.target.value.length >= minSearchLen) {
            matches.classList.remove("d-none")
            search(e.target.value)
        } else {
            matches.innerHTML = "";
            matches.classList.add("d-none")
        }
    }
}

// prevent the up and down arrows changing the cursor position in the input box
function disableArrowKeys(e) {
    switch (e.key) {
        case "ArrowUp":
        case "ArrowDown":
            e.preventDefault();
            break;
    }
}

function highlight(pos) {
    let item = document.getElementById("search-item-" + pos)
    if (item != null) {
        item.parentElement.parentElement.classList.add("search-match-selected")
    }
}

function unhighlight(pos) {
    let item = document.getElementById("search-item-" + pos)
    if (item != null) {
        item.parentElement.parentElement.classList.remove("search-match-selected")
    }
}

document.getElementById("searchicon").addEventListener("click", function (e) {
    e.stopImmediatePropagation();
    toggleSearch();
}, true)

document.getElementById("catalogue-search").addEventListener("focus", function (e) {
    e.stopImmediatePropagation();
    showSearchBar();
}, true)

input.addEventListener("keyup", searchInputListener, false)
input.addEventListener("keydown", disableArrowKeys, false)
document.getElementById("standard-layout-container").addEventListener("click", e => hideSearchBar(), true)
document.addEventListener("keyup", globalSearchShortcut, false)
