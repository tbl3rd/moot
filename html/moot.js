function newMap() {
    var div = document.createElement('div');
    div.setAttribute('id', 'map-canvas');
    document.body.appendChild(div);
    var mapOptions = {
        center: { lat: -34.397, lng: 150.644 },
        zoom: 8
    };
    var map = new google.maps.Map(div, mapOptions);
}

function loadMap() {
    var key = 'AIzaSyA10Vv0gV5yLp1WcTpqoA9hhILt_Rhc6OQ';
    var api = 'https://maps.googleapis.com/maps/api/js?' + '&v=3';
    var script = document.createElement('script');
    script.type = 'text/javascript';
    script.src = api + '&key=' + key + '&signed_in=true' + '&callback=newMap';
    document.head.appendChild(script);
}

window.onload = loadMap;
