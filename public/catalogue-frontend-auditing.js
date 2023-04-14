$(function() {


    const sendAudit = (body) => {
        //const auditData = new Blob([JSON.stringify(body)]);
        navigator.sendBeacon("/audit", decodeURI(body));
        return true;
    }

    //  const addExternalLinkAuditEvent = function(tag, detail) {
    //   document.querySelectorAll(tag).forEach(function(current) {
    //     current.addEventListener("click", function(e) { return sendAudit(detail);});
    //   });
    // };

    // most links
    document.querySelectorAll("a").forEach(function(current) {
        if(current.href.trim().length > 0) { // not empty
            current.addEventListener("click", function(e) { return sendAudit(this.href); });
        }
    });

    // links embedded in a bootstrap popover
    $("[data-toggle=popover]").on("show.bs.popover", function () {
        document.querySelectorAll("a.popover-link").forEach(function(current) {
            if(current.href.trim.length > 0) { // not empty
                console.log("hello")
                current.addEventListener("click", function(e) { return sendAudit(this.href); });
            }
        });
    });

    // buttons
    document.querySelectorAll("button, [type=button]").forEach(function(current) {
        current.addEventListener("click", function(e) { return sendAudit(this.id); });
    });






});