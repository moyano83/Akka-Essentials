# Akka Esentials

## Akka Actors
Questions that are relevant to actors:

    * Can we assume any ordering of messages?
    * How are racing conditions handled?
    
Akka has a pool of threads that is shared among actors, it also has a message queue or `mailbox`. Actors are just a data
structure which is passive and needs a thread to run it, akka just schedules the actors in the threads of the threadpool.
To send a message, the message is queued in the actor mailbox and later scheduled by the akka system to run in a
threadpool which invokes the message handle for the message type received, after that the message is discarded. This
whole process guarantees:
  
    * Actors are effectively single threaded
    * No locks are needed
    * The processing message is atomic
 
The message delivery guarantees:

    * The message is delivered at most once
    * For any message-receive pair, the message order is always maintained    