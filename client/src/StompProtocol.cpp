#include "../include/StompProtocol.h"
#include <iostream>
#include <sstream>
#include <fstream>
#include "../include/event.h"
#include <iomanip>


StompProtocol::StompProtocol(ConnectionHandler &handler)
    : connectionHandler(handler), isLoggedIn(false),currentUser(""), receiptIdCounter(0),subscriptionId(0), pendingReceipts(),channelAndSubscribedId(), receivedReports(), sentReportsCount(0), 
      receivedReportsCount(0){}

bool StompProtocol::processCommand(const std::string &command) {
    std::stringstream ss(command);
    std::string action;
    ss >> action;

    if (action == "login") {
        if (!connectionHandler.isConnected()) {
            connectionHandler.connect();
            }
        std::string host, username, password;
        if (!(ss >> host >> username >> password)) {
            std::cout << "Invalid login command. Usage: login <host> <username> <password>" << std::endl;
            return false;
        }
        if (host.empty() || username.empty() || password.empty()) {
            std::cout << "Error: All login fields (host, username, password) must be provided." << std::endl;
            return false;
        }

        std::string connectFrame = buildConnectFrame(host, username, password);

        if (connectionHandler.sendLine(connectFrame)) {
            currentUser = username;
            return true;
        } else {
            std::cout << "Could not connect to server" << std::endl;
            return false;
        }
    }
    else if (action == "join") {
        std::string channel;
        ss >> channel;
        std::string subscribeFrame = buildSubscribeFrame(channel);
        connectionHandler.sendLine(subscribeFrame);
    }
    else if (action == "exit") {
        std::string channel;
        ss >> channel;

        std::string unsubscribeFrame = buildUnsubscribeFrame(channel);
        connectionHandler.sendLine(unsubscribeFrame);
    }
    else if (action == "report") {
        std::string filename;
        ss >> filename;

        try {
            names_and_events eventsData = parseEventsFile(filename);
            
            for (const Event &event : eventsData.events) {
                std::string frame = buildReportFrame(event, currentUser);
                receivedReports[event.get_channel_name()][currentUser].push_back(event);
                connectionHandler.sendLine(frame);
                sentReportsCount++;
            }
        } catch (const std::exception &e) {
            std::cerr << "Failed to parse the report file: " << e.what() << std::endl;
        }
    }
    else if (action == "summary") {
            std::string channel, user, filename;
            ss >> channel >> user >> filename;

            if (generateSummary(channel, user, filename)) {
                std::cout << "Summary written to " << filename << std::endl;
            }
    }
    else if (action == "logout") {
        std::string disconnectFrame = buildDisconnectFrame();
        connectionHandler.sendLine(disconnectFrame);
        return true;
    }
    else {
        std::cout << "Unknown command: " << action << std::endl;
    }
    return true;
}

void StompProtocol::processServerResponse(const std::string &response) {
    if (response.find("CONNECTED") != std::string::npos) {
        std::cout << "Login successful" << std::endl;
        isLoggedIn = true;
    }
    else if (response.find("ERROR") != std::string::npos) {
        handleErrorFrame(response);
    }
    else if (response.find("RECEIPT") != std::string::npos) {
        handleReceiptFrame(response);
    } 
    else if (response.find("MESSAGE") != std::string::npos) {
        handleMessageFrame(response);
        receivedReportsCount++;
        if (allReportsReceived()) {
            std::cout << "reported" << std::endl;
        }
    }
}

bool StompProtocol::allReportsReceived() const {
    return sentReportsCount == receivedReportsCount;
}


std::string StompProtocol::buildConnectFrame(const std::string &host, const std::string &user, const std::string &pass) {
    std::stringstream frame;
    frame << "CONNECT\n";
    frame << "accept-version:1.2\n";
    frame << "host:stomp.cs.bgu.ac.il\n";
    frame << "login:" << user << "\n";
    frame << "passcode:" << pass << "\n";
    frame << "\n\0"; 
    return frame.str();
}

std::string StompProtocol::buildSubscribeFrame(const std::string &channel) {
    std::stringstream frame;
    receiptIdCounter++;
    frame << "SUBSCRIBE\n";
    frame << "destination:/ " << channel << "\n";
    frame << "id:" <<  subscriptionId<< "\n";
    frame << "receipt:" << receiptIdCounter << "\n";
    frame << "\n\0";
    pendingReceipts[receiptIdCounter] = "subscribe to " + channel;
    channelAndSubscribedId[channel] = subscriptionId;
    subscriptionId++;
    return frame.str();
}

std::string StompProtocol::buildUnsubscribeFrame(const std::string &channel) {
    std::stringstream frame;
    receiptIdCounter++;
    frame << "UNSUBSCRIBE\n";
    frame << "id:" << channelAndSubscribedId[channel] << "\n";
    frame << "receipt:" << receiptIdCounter << "\n";
    frame << "\n\0";
    pendingReceipts[receiptIdCounter] = "unsubscribe from " + channel;
    channelAndSubscribedId.erase(channel);
    return frame.str();
}

std::string StompProtocol::buildReportFrame(const Event &event, const std::string &user) {
    std::stringstream frame;
    frame << "SEND\n";
    frame << "destination:/" << event.get_channel_name() << "\n";
    frame << "user:" << user << "\n";
    frame << "city:" << event.get_city() << "\n";
    frame << "event name:" << event.get_name() << "\n";
    frame << "date time:" << event.get_date_time() << "\n";
    frame << "general information:\n";
    
    for (const auto &info : event.get_general_information()) {
        frame << " " << info.first << ":" << info.second << "\n";
    }

    frame << "description:\n";
    frame << event.get_description() << "\n";
    frame << "\n\0"; 
    
    return frame.str();
}

std::string StompProtocol::buildDisconnectFrame() {
    receiptIdCounter++;
    std::stringstream frame;
    frame << "DISCONNECT\n";
    frame << "receipt:" << receiptIdCounter << "\n";
    frame << "\n\0";
    pendingReceipts[receiptIdCounter] = "disconnect";
    return frame.str();
}

bool StompProtocol::isLogged() const {
    return isLoggedIn;
}

void StompProtocol::setLoggedIn(bool status) {
    isLoggedIn = status;
}

void StompProtocol::handleMessageFrame(const std::string &frame) {
    std::stringstream ss(frame);
    std::string line;
    std::string subscription, messageId, destination, body;
    
    while (std::getline(ss, line) && !line.empty()) {
        if (line.find("subscription:") == 0) {
            subscription = line.substr(13);  
        }
        else if (line.find("message-id:") == 0) {
            messageId = line.substr(11);  
        }
        else if (line.find("destination:") == 0) {
            destination = line.substr(12);  
        }
    }

    while (std::getline(ss, line)) {
        body += line + "\n";
    }
}

bool StompProtocol::generateSummary(const std::string &channel, const std::string &user, const std::string &filename) {
    auto channelIt = receivedReports.find(channel);
    // בודק אם הערוץ לא נמצא במפה או אם המשתמש לא נמצא בערוץ 
    if (channelIt->second.find(user) == channelIt->second.end()) {
        std::cerr << "No reports available for user " << user << " in channel, you are not subscribe to the channel" << channel << std::endl;
        return false;
    }
    // אם המשתמש לא נמצא בערוץ
    if (channelIt == receivedReports.end()) {
        std::cerr << "The channel "<< channel << " does not exist" <<std::endl;
        return false;
    }

    const std::vector<Event> &events = channelIt->second[user];

    std::ofstream outputFile(filename); 
    if (!outputFile.is_open()) {
        std::cerr << "Error: Could not open file " << filename << std::endl;
        return false;
    }

    outputFile << "Channel " << channel << "\n";
    outputFile << "Stats:\n";
    outputFile << "Total: " << events.size() << "\n";

    int activeCount = 0;
    int forcesArrivalCount = 0;
    
    for (const auto &event : events) {
        if (event.get_general_information().at("active") == "true") {
            activeCount++;
        }
        if (event.get_general_information().at("forces_arrival_at_scene") == "true") {
            forcesArrivalCount++;
        }
    }

    outputFile << "active: " << activeCount << "\n";
    outputFile << "forces arrival at scene: " << forcesArrivalCount << "\n";
    outputFile << "Event Reports:\n";

    std::vector<Event> sortedEvents = events;
    std::sort(sortedEvents.begin(), sortedEvents.end(), [](const Event &a, const Event &b) {
        if (a.get_date_time() == b.get_date_time()) {
            return a.get_name() < b.get_name();
        }
        return a.get_date_time() < b.get_date_time();
    });

    int reportCount = 1;
    for (const auto &event : sortedEvents) {
        outputFile << "Report_" << reportCount++ << ":\n";
        outputFile << "city: " << event.get_city() << "\n";
        outputFile << "date time: " << epoch_to_date(event.get_date_time()) << "\n";
        outputFile << "event name: " << event.get_name() << "\n";
        
        std::string summary = event.get_description();
        if (summary.length() > 27) {
            summary = summary.substr(0, 27) + "...";
        }
        outputFile << "summary: " << summary << "\n";
    }

    outputFile.close();
    return true;
}


std::string StompProtocol::epoch_to_date(int epochTime) {
    std::time_t t = static_cast<std::time_t>(epochTime);
    std::tm *tm = std::localtime(&t);
    char buffer[20];
    strftime(buffer, sizeof(buffer), "%d/%m/%y %H:%M", tm);
    return std::string(buffer);
}

void StompProtocol::handleReceiptFrame(const std::string &frame) {
    std::stringstream ss(frame);
    std::string line;
    std::string receiptId;
    std::string channel;

    while (std::getline(ss, line) && !line.empty()) {
        if (line.find("receipt-id:") == 0) {
            receiptId = line.substr(11);  
            break;
        }
    }
    if (receiptId.empty()) {
        std::cerr << "Error: No receipt-id found in frame" << std::endl;
        return;
    }try {
        int receiptKey = std::stoi(receiptId);
        auto it = pendingReceipts.find(receiptKey);

        if (it != pendingReceipts.end()) {
            std::string receiptVal = it->second;

            if (receiptVal.find("subscribe to ") == 0) {
                size_t pos = receiptVal.find("subscribe to ");
                channel = receiptVal.substr(pos + 13);                
                std::cout << "Joined channel " << channel << std::endl;
            } else if (receiptVal.find("unsubscribe from ") == 0) {
                size_t pos = receiptVal.find("unsubscribe from ");
                channel = receiptVal.substr(pos + 17);
                std::cout << "Exited channel " << channel << std::endl;
            } else if (receiptVal.find("disconnect") == 0) {
                std::cout << "Logged out" << std::endl;
                currentUser.clear();
                pendingReceipts.clear();
                receivedReports.clear();
                isLoggedIn = false;
                connectionHandler.close();
            } else {
                std::cerr << "Unknown receipt value: " << receiptVal << std::endl;
            }
        } else {
            std::cerr << "Error: Receipt ID not found in pendingReceipts" << std::endl;
        }

    } catch (const std::exception &e) {
        std::cerr << "Error processing receipt ID: " << e.what() << std::endl;
    }       
}


void StompProtocol::handleErrorFrame(const std::string &frame) {
    std::stringstream ss(frame);
    std::string line;
    std::string errorMessage;

    while (std::getline(ss, line) && !line.empty()) {
        if (line.find("message:") == 0) {
            errorMessage = line.substr(8);  
            std::cout << errorMessage << std::endl;
            break;
        }
    }connectionHandler.close();
}