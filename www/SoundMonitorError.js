var SoundMonitorError = function(err) {
    this.code = (err !== undefined ? err : null);
};

SoundMonitorError.SOUNDMONITOR_INTERNAL_ERR = 0;
SoundMonitorError.SOUNDMONITOR_NOT_SUPPORTED = 20;

module.exports = SoundMonitorError;