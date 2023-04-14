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

    document.querySelectorAll("a").forEach(function(current) {
        current.addEventListener("click", function(e) { return sendAudit(this.href); });
    });

    document.querySelectorAll("button, [type=button]").forEach(function(current) {
        current.addEventListener("click", function(e) { return sendAudit(this.id); });
    });






});