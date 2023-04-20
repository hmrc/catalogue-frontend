$(function() {
    const sendAudit = (href, id) => {
        const body = {
            "target": decodeURIComponent(href),
            "id": id
        };

        const replacer = function(k, v) { if (v === "") { return null; } return v; };

        const auditData = new Blob([JSON.stringify(body, replacer)], { type: "application/json" });
        navigator.sendBeacon("/audit", auditData);
        return true;
    }

    document.querySelectorAll("a, button, .btn, [type=button], select").forEach(function(current) {
        current.addEventListener("click", function(e) { return sendAudit(this.href, this.id); });
    });

});
