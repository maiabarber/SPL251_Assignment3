#include <iostream>
#include <thread>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"



int main(int argc, char *argv[]) {
    std::string inputFromClient;
    getline(std::cin, inputFromClient);
    std::istringstream iss(inputFromClient);
    std::string task, hostport;
    iss >> task >> hostport;

    size_t colonPos = hostport.find(':');
    
    std::string host = hostport.substr(0, colonPos);
    short port = std::stoi(hostport.substr(colonPos + 1));
    

    ConnectionHandler connectionHandler(host, port);

    if (!connectionHandler.connect()) {
        std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
        return 1;
    }

	StompProtocol protocol(connectionHandler);//בנאי מאתחל logged in לfalse וreceiptcounter ל0

    // Thread to handle user input
    std::thread inputThread([&protocol, &connectionHandler,&inputFromClient]() {
        std::string input;
        bool firstLogin=true;
        while (true) {
            if(firstLogin){
                input=inputFromClient;
                firstLogin=false;
            }else{getline(std::cin, input);}
            if (!connectionHandler.isConnected()&&input.find("login")==std::string::npos) {
                std::cerr << "connectionHandler is Closed! closing inputThread" << std::endl;
                break;
            }
            if (!protocol.processCommand(input)) {
                break;
            }
        }
    });

    
    // Thread to handle server responses
    std::thread responseThread([&protocol, &connectionHandler]() {
        std::string response;
        while(true){
            while ((connectionHandler.isConnected() && connectionHandler.getLine(response))) {
                protocol.processServerResponse(response);
                response = "";
            }
        }
    });

    inputThread.join();
    responseThread.join();

    return 0;
}
