$(function() { // onload...do
    $("#loginForm").submit(function() {
        var parameters = $(this).serialize();

        $.ajax({
            data: parameters,
            url: this.action,
            timeout: 2000,
            error: function() {
                console.error("Error from server!");
            },
            success: function(data) {
              if (data == "success") {
                location.href = "main.html";
              } else {
                var x = document.getElementById("errorblock");
                x.style.display = "block";
                x.innerHTML = data;
              }
            }
        });

        return false;
    })
})