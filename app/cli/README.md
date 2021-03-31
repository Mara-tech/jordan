# Jordan cli

Jordan-cli is based on [Jordan Python library](../../libraries/python), and is aimed to create a 
Jordan client with no action, but will send statuses from what's on `stdin`.
You will be able to keep track on long running shell tasks, such as builds, up/downloads or deployments.

## Usage
    tail -f logs.txt | python jordan_cli.py
    
    
### Make alias
If you always use Jordan in the same way, consider creating an alias, looking like :

    alias jordan='python /path/to/jordan_cli.py [--yml-conf /path/to/conf.yml]'
So that usage is much simpler :

    tail -f logs.txt | jordan
 
### Arguments
Enter `python jordan_cli.py -h` to see all optional arguments.

### Some examples
    
[`docker build . | jordan`](https://docs.docker.com/engine/reference/commandline/build/)
    
[`copilot deploy | jordan`](https://aws.github.io/copilot-cli/docs/commands/deploy/)

`wget http://cdimage.ubuntu.com/releases/20.10/release/ubuntu-20.10-live-server-arm64.iso | jordan`


## YAML Configuration
Optional configuration : 
- `url` : base URL for Jordan server. e.g : http://192.168.1.34:5000/jordan/
- `forward-stdout` : Whether or not stdout should be forwarded though jordan. Accepted valued : `True` or `False`.

Values from YAML configuration are ignored if they are specified as arguments in CLI.

## Progress and ProgressBar
Unfortunately, progress, even with progress bar frameworks 
(such as [tqdm](https://github.com/tqdm/tqdm), 
[progressbar2](https://progressbar-2.readthedocs.io/en/latest/index.html)), 
is output to stderr, so is unreadable with Jordan CLI.
Full integration of Jordan with [librairies](../../libraries) is the only solution to have more information 
than simple status formed with lines of stdout.