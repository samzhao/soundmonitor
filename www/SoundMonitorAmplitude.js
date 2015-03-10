var SoundMonitorAmplitude = function(amp, timestamp) {
    this.value = amp;
    this.timestamp = timestamp || new Date().getTime();
};

module.exports = SoundMonitorAmplitude;