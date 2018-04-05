# Standalone execution of son-sp-infraabstract and vim-emu
Vim-adaptor is built with Java9
## Set up environment
1. Place files in /etc/... (to do)
2. Start the database:
`docker run -d -p 5432:5432 --name son-postgres -e POSTGRES_DB=gatekeeper -e POSTGRES_USER=sonatatest -e POSTGRES_PASSWORD=sonata ntboes/postgres-uuid`
3. Start vim-emu
`sudo docker run --name vim-emu -p 5001:5001 -it --rm --privileged --pid='host' -v /var/run/docker.sock:/var/run/docker.sock vim-emu-img /bin/bash` 
4. Execute `python examples/osm_default_daemon_topology_2_pop.py` in vim-emu docker container

For further information consult [vim-emu wiki](https://osm.etsi.org/wikipub/index.php/VIM_emulator)
## Execute tests
Now, everything is ready for executing tests.
For example, you can execute the VIMEmuIntegrationTest:
1. Clone repo and start maven workflow producing JARs
2. Invoke VIMEmuIntegrationTest

`java -cp /usr/share/java/junit.jar:adaptor-0.0.1-SNAPSHOT-jar-with-dependencies.jar org.junit.runner.JUnitCore  sonata.kernel.vimadaptor.VIMEmuIntegrationTest`