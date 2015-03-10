var argscheck = require('cordova/argscheck');
var exec = require('cordova/exec');
var utils = require('cordova/utils');
var SoundMonitorAmplitude = require('./SoundMonitorAmplitude');
var SoundMonitorError = require('./SoundMonitorError');

var timers = {};
var SoundMonitor = {
    getCurrentAmplitude.function(successCallback, errorCallback, options) {
        argscheck.checkArgs('fFO', 'SoundMonitor.getCurrentAmplitude', arguments);

        var win = function (result) {
            var ch = new SoundMonitorAmplitude(result.value, result.timestamp);
            successCallback(ch);
        };

        var fail = errorCallback && function(code) {
            var ce = new SoundMonitorError(code);
            errorCallback(ce);
        };

        exec(win, fail, "SoundMonitor", "getAmplitude", [options]);
    },

    watchAmplitude: function(successCallback, errorCallback, options) {
        argscheck.checkArgs('fFO', 'SoundMonitor.watchAmplitude', arguments);

        var frequency = (options !== undefined && options.frequency !== undefined) ? options.frequency : 100;
        var filter = (options !== undefined && options.filter !== undefined) ? options.filter : 0;

        var id = utils.createUUID();
        if (filter > 0) {
            timers[id] = "iOS";
            SoundMonitor.getCurrentAmplitude(successCallback, errorCallback, options);
        } else {
            timers[id] = window.setInterval(function() {
                SoundMonitor.getCurrentAmplitude(successCallback, errorCallback);
            }, frequency);
        }

        return id;
    };

    clearWatch: function(id) {
        if (id && timers[id]) {
            if (timers[id] != "iOS") {
                clearInterval(timers[id]);
            } else {
                exec(null, null, "SoundMonitor", "stopAmplitude", []);
            }
            delete timers[id];
        }
    }
};

module.exports = SoundMonitor;