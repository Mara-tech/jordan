def log_level(level, message):
    print(f"[{level}] {message}")


def debug(message):
    log_level("DEBUG", message)

def info(message):
    log_level("INFO", message)

def error(message):
    log_level("ERROR", message)