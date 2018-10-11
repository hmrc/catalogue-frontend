const COLUMNS = 8;
const GUTTER_SIZE = 10;

ready(function() {
    Split(["#header-0", "#header-1", "#header-2", "#header-3", "#header-4", "#header-5", "#header-6", "#header-7"], {
        sizes: [300, 500, 500, 500, 500, 500, 500, 500],
        elementStyle: function (el, offset) {
            const cells = document.getElementsByClassName("grid-item");
            const index = [].indexOf.call(el.parentElement.children, el) / 2;

            for (var i = index; i < cells.length; i += COLUMNS) {
                const element = cells[i];
                element.style["width"] = `${offset + GUTTER_SIZE}px`
            }

            el.style["width"] = `${offset}px`;
        }
    })
});

function ready(fn) {
    if (document.attachEvent ? document.readyState === "complete" : document.readyState !== "loading"){
        fn();
    } else {
        document.addEventListener('DOMContentLoaded', fn);
    }
}